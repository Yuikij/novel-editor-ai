package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.soukon.novelEditorAi.entities.*;
import com.soukon.novelEditorAi.events.DataChangeEvent;
import com.soukon.novelEditorAi.mapper.*;
import com.soukon.novelEditorAi.service.UnifiedVectorSyncService;
import com.soukon.novelEditorAi.service.CharacterRelationshipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一的向量同步服务实现
 */
@Service
@Slf4j
public class UnifiedVectorSyncServiceImpl implements UnifiedVectorSyncService {

    @Autowired
    private VectorStore vectorStore;
    
    @Autowired
    private VectorSyncTaskMapper vectorSyncTaskMapper;
    
    @Autowired
    private DataChangeEventRecordMapper eventRecordMapper;
    
    @Autowired
    private ProjectMapper projectMapper;
    
    @Autowired
    private ChapterMapper chapterMapper;
    
    @Autowired
    private CharacterMapper characterMapper;
    
    @Autowired
    private PlotMapper plotMapper;
    
    @Autowired
    private WorldMapper worldMapper;
    
    @Autowired
    private CharacterRelationshipService characterRelationshipService;

    @Value("${novel.vector.chunk-size:500}")
    private int chunkSize;

    @Value("${novel.vector.chunk-overlap:100}")
    private int chunkOverlap;

    @Value("${novel.vector.max-retries:3}")
    private int maxRetries;

    // 处理中的任务缓存，防止重复处理
    private final Set<String> processingTasks = ConcurrentHashMap.newKeySet();

    @Override
    @EventListener
    @Async
    public void handleDataChangeEvent(DataChangeEvent event) {
        log.info("处理数据变更事件: {}", event);
        
        try {
            // 记录事件到数据库
            DataChangeEventRecord record = new DataChangeEventRecord(
                    event.getEntityType(), event.getEntityId(), 
                    event.getChangeType(), event.getVersion()
            );
            eventRecordMapper.insert(record);
            
            // 创建同步任务
            String operation = determineOperation(event.getChangeType());
            VectorSyncTask task = new VectorSyncTask(
                    event.getEntityType(), event.getEntityId(), operation, event.getVersion()
            );
            
            // 检查是否已存在相同版本的任务
            if (!taskExists(task)) {
                vectorSyncTaskMapper.insert(task);
                
                // 如果是紧急任务，立即处理
                if (event.isUrgent()) {
                    syncEntity(event.getEntityType(), event.getEntityId(), operation, event.getVersion());
                }
            }
            
            // 标记事件已处理
            eventRecordMapper.markEventProcessed(record.getId());
            
        } catch (Exception e) {
            log.error("处理数据变更事件失败: {}", event, e);
        }
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void syncEntity(String entityType, Long entityId, String operation, Long version) {
        String taskKey = entityType + ":" + entityId + ":" + version;
        
        if (processingTasks.contains(taskKey)) {
            log.debug("任务正在处理中，跳过: {}", taskKey);
            return;
        }
        
        processingTasks.add(taskKey);
        
        try {
            log.info("开始同步实体: type={}, id={}, operation={}, version={}", 
                    entityType, entityId, operation, version);
            
            // 更新状态为处理中
            updateVectorStatus(entityType, entityId, "PROCESSING", null);
            
            switch (operation) {
                case "INDEX":
                case "UPDATE":
                    processVectorization(entityType, entityId, version);
                    break;
                case "DELETE":
                    processVectorDeletion(entityType, entityId);
                    break;
                default:
                    log.warn("未知的操作类型: {}", operation);
                    return;
            }
            
            // 更新状态为已完成
            updateVectorStatus(entityType, entityId, "INDEXED", null);
            updateVectorLastSync(entityType, entityId);
            
            log.info("同步实体完成: type={}, id={}", entityType, entityId);
            
        } catch (Exception e) {
            log.error("同步实体失败: type={}, id={}, operation={}", entityType, entityId, operation, e);
            updateVectorStatus(entityType, entityId, "FAILED", e.getMessage());
            throw e;
        } finally {
            processingTasks.remove(taskKey);
        }
    }

    @Override
    public boolean isContentChanged(String oldContent, String newContent) {
        if (oldContent == null && newContent == null) {
            return false;
        }
        if (oldContent == null || newContent == null) {
            return true;
        }
        
        // 使用MD5哈希比较内容
        return !Objects.equals(hashContent(oldContent), hashContent(newContent));
    }

    @Override
    public List<Document> createVectorDocuments(String entityType, Long entityId, Long version) {
        try {
            Object entity = getEntityById(entityType, entityId);
            if (entity == null) {
                log.warn("实体不存在: type={}, id={}", entityType, entityId);
                return Collections.emptyList();
            }
            
            String content = extractContent(entity, entityType);
            if (!StringUtils.hasText(content)) {
                log.warn("实体内容为空: type={}, id={}", entityType, entityId);
                return Collections.emptyList();
            }
            
            Map<String, Object> baseMetadata = createBaseMetadata(entity, entityType, version);
            
            // 如果内容较短，直接创建单个文档
            if (content.length() <= chunkSize) {
                Document document = new Document(
                        entityType + "-" + entityId + "-v" + version,
                        content,
                        baseMetadata
                );
                return Collections.singletonList(document);
            }
            
            // 内容较长，分片处理
            return createChunkedDocuments(content, entityType, entityId, version, baseMetadata);
            
        } catch (Exception e) {
            log.error("创建向量文档失败: type={}, id={}", entityType, entityId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void markOldVersionsAsDeprecated(String entityType, Long entityId, Long version) {
        try {
            // 删除旧版本文档
            String[] filterExpressions = {
                String.format("entityType == '%s' && entityId == '%s' && version < %d", entityType, entityId, version),
                String.format("entityType == \"%s\" && entityId == \"%s\" && version < %d", entityType, entityId, version)
            };
            
            for (String filter : filterExpressions) {
                try {
                    vectorStore.delete(filter);
                    log.debug("删除旧版本文档: type={}, id={}, version={}, filter={}", entityType, entityId, version, filter);
                    break;
                } catch (Exception e) {
                    log.debug("删除过滤器失败: {}", filter);
                }
            }
            
        } catch (Exception e) {
            log.warn("标记旧版本失败: type={}, id={}", entityType, entityId, e);
        }
    }

    @Override
    public void deleteEntityVectorDocuments(String entityType, Long entityId) {
        try {
            String[] filterExpressions = {
                String.format("entityType == '%s' && entityId == '%s'", entityType, entityId),
                String.format("entityType == \"%s\" && entityId == \"%s\"", entityType, entityId),
                String.format("id like '%s-%s-%%'", entityType, entityId)
            };
            
            for (String filter : filterExpressions) {
                try {
                    vectorStore.delete(filter);
                    log.debug("删除实体向量文档成功: type={}, id={}, filter={}", entityType, entityId, filter);
                    break;
                } catch (Exception e) {
                    log.debug("删除过滤器失败: {}, 尝试下一个", filter);
                }
            }
            
        } catch (Exception e) {
            log.error("删除实体向量文档失败: type={}, id={}", entityType, entityId, e);
        }
    }

    @Override
    public void updateVectorStatus(String entityType, Long entityId, String status, String errorMessage) {
        try {
            switch (entityType) {
                case "project":
                    updateProjectVectorStatus(entityId, status, errorMessage);
                    break;
                case "chapter":
                    updateChapterVectorStatus(entityId, status, errorMessage);
                    break;
                case "character":
                    updateCharacterVectorStatus(entityId, status, errorMessage);
                    break;
                case "plot":
                    updatePlotVectorStatus(entityId, status, errorMessage);
                    break;
                case "world":
                    updateWorldVectorStatus(entityId, status, errorMessage);
                    break;
                default:
                    log.warn("未知的实体类型: {}", entityType);
            }
        } catch (Exception e) {
            log.error("更新向量状态失败: type={}, id={}, status={}", entityType, entityId, status, e);
        }
    }

    @Override
    public void processBatchEvents(List<DataChangeEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        // 合并同一实体的多次变更，只保留最新版本
        Map<String, DataChangeEvent> latestEvents = new HashMap<>();
        for (DataChangeEvent event : events) {
            String key = event.getEntityType() + ":" + event.getEntityId();
            DataChangeEvent existing = latestEvents.get(key);
            
            if (existing == null || event.getVersion() > existing.getVersion()) {
                latestEvents.put(key, event);
            }
        }
        
        // 按优先级排序：DELETE > UPDATE > CREATE
        List<DataChangeEvent> sortedEvents = latestEvents.values().stream()
                .sorted(this::compareEventPriority)
                .collect(Collectors.toList());
        
        // 批量处理
        for (DataChangeEvent event : sortedEvents) {
            try {
                handleDataChangeEvent(event);
            } catch (Exception e) {
                log.error("批量处理事件失败: {}", event, e);
            }
        }
    }

    @Override
    public void recoverFailedTasks() {
        try {
            List<VectorSyncTask> failedTasks = vectorSyncTaskMapper.selectFailedTasks(maxRetries, 100);
            
            for (VectorSyncTask task : failedTasks) {
                try {
                    log.info("恢复失败任务: {}", task);
                    
                    task.setRetryCount(task.getRetryCount() + 1);
                    task.setStatus("PENDING");
                    task.setErrorMessage(null);
                    vectorSyncTaskMapper.updateById(task);
                    
                    // 异步重新处理
                    syncEntity(task.getEntityType(), task.getEntityId(), task.getOperation(), task.getVersion());
                    
                } catch (Exception e) {
                    log.error("恢复任务失败: {}", task, e);
                    
                    // 如果超过最大重试次数，标记为放弃
                    if (task.getRetryCount() >= maxRetries) {
                        task.setStatus("ABANDONED");
                        task.setErrorMessage("超过最大重试次数: " + e.getMessage());
                        vectorSyncTaskMapper.updateById(task);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("恢复失败任务过程出错", e);
        }
    }

    @Override
    public void cleanupDeprecatedDocuments() {
        try {
            // 清理24小时前标记为DEPRECATED的文档
            long cutoffTime = System.currentTimeMillis() - 24 * 3600 * 1000;
            
            String[] filterExpressions = {
                "status == 'DEPRECATED' && lastModified < " + cutoffTime,
                "status == \"DEPRECATED\" && lastModified < " + cutoffTime
            };
            
            for (String filter : filterExpressions) {
                try {
                    vectorStore.delete(filter);
                    log.info("清理废弃文档完成: {}", filter);
                } catch (Exception e) {
                    log.debug("清理过滤器失败: {}", filter);
                }
            }
            
        } catch (Exception e) {
            log.error("清理废弃文档失败", e);
        }
    }

    @Recover
    public void recover(Exception ex, String entityType, Long entityId, String operation, Long version) {
        log.error("同步实体最终失败: type={}, id={}, operation={}", entityType, entityId, operation, ex);
        updateVectorStatus(entityType, entityId, "FAILED", ex.getMessage());
    }

    // 私有辅助方法

    private void processVectorization(String entityType, Long entityId, Long version) {
        // 标记旧版本为废弃
        markOldVersionsAsDeprecated(entityType, entityId, version);
        
        // 创建新版本向量文档
        List<Document> documents = createVectorDocuments(entityType, entityId, version);
        
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("成功添加 {} 个向量文档: type={}, id={}", documents.size(), entityType, entityId);
        }
    }

    private void processVectorDeletion(String entityType, Long entityId) {
        deleteEntityVectorDocuments(entityType, entityId);
        log.info("删除实体向量文档: type={}, id={}", entityType, entityId);
    }

    private String determineOperation(String changeType) {
        switch (changeType) {
            case "CREATE":
                return "INDEX";
            case "UPDATE":
                return "UPDATE";
            case "DELETE":
                return "DELETE";
            default:
                return "INDEX";
        }
    }

    private boolean taskExists(VectorSyncTask task) {
        return vectorSyncTaskMapper.selectCount(
                new UpdateWrapper<VectorSyncTask>()
                        .eq("entity_type", task.getEntityType())
                        .eq("entity_id", task.getEntityId())
                        .eq("version", task.getVersion())
        ) > 0;
    }

    private Object getEntityById(String entityType, Long entityId) {
        switch (entityType) {
            case "project":
                return projectMapper.selectById(entityId);
            case "chapter":
                return chapterMapper.selectById(entityId);
            case "character":
                return characterMapper.selectById(entityId);
            case "plot":
                return plotMapper.selectById(entityId);
            case "world":
                return worldMapper.selectById(entityId);
            default:
                return null;
        }
    }

    private String extractContent(Object entity, String entityType) {
        StringBuilder content = new StringBuilder();
        
        switch (entityType) {
            case "project":
                Project project = (Project) entity;
                content.append("=== 项目信息 ===\n");
                appendIfNotNull(content, "项目标题", project.getTitle());
                appendIfNotNull(content, "项目简介", project.getSynopsis());
                appendIfNotNull(content, "项目类型", project.getGenre());
                appendIfNotNull(content, "写作风格", project.getStyle());
                appendIfNotNull(content, "目标受众", project.getTargetAudience());
                appendIfNotNull(content, "项目类型结构", project.getType());
                if (project.getWordCountGoal() != null) {
                    content.append("目标字数: ").append(project.getWordCountGoal()).append("\n");
                }
                appendIfNotNull(content, "项目状态", project.getStatus());
                if (project.getTags() != null && !project.getTags().isEmpty()) {
                    content.append("项目标签: ").append(String.join(", ", project.getTags())).append("\n");
                }
                if (project.getHighlights() != null && !project.getHighlights().isEmpty()) {
                    content.append("项目亮点: ").append(String.join(", ", project.getHighlights())).append("\n");
                }
                if (project.getWritingRequirements() != null && !project.getWritingRequirements().isEmpty()) {
                    content.append("写作要求: ").append(String.join(", ", project.getWritingRequirements())).append("\n");
                }
                break;
                
            case "chapter":
                Chapter chapter = (Chapter) entity;
                content.append("=== 章节信息 ===\n");
                appendIfNotNull(content, "章节标题", chapter.getTitle());
                if (chapter.getSortOrder() != null) {
                    content.append("章节序号: 第").append(chapter.getSortOrder()).append("章\n");
                }
                appendIfNotNull(content, "章节类型", chapter.getType());
                appendIfNotNull(content, "章节状态", chapter.getStatus());
                if (chapter.getWordCountGoal() != null) {
                    content.append("目标字数: ").append(chapter.getWordCountGoal()).append("\n");
                }
                                 // 计算实际字数（如果有内容的话）
                 if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()) {
                     int actualWordCount = chapter.getContent().trim().length();
                     content.append("实际字数: ").append(actualWordCount).append("\n");
                 }
                appendIfNotNull(content, "章节摘要", chapter.getSummary());
                appendIfNotNull(content, "章节备注", chapter.getNotes());
                
                // 章节内容是最重要的部分，放在最后
                if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()) {
                    content.append("\n=== 章节正文内容 ===\n");
                    content.append(chapter.getContent()).append("\n");
                }
                break;
                
            case "character":
                com.soukon.novelEditorAi.entities.Character character = (com.soukon.novelEditorAi.entities.Character) entity;
                content.append("=== 角色信息 ===\n");
                appendIfNotNull(content, "角色名称", character.getName());
                appendIfNotNull(content, "角色类型", character.getRole());
                appendIfNotNull(content, "性别", character.getGender());
                if (character.getAge() != null) {
                    content.append("年龄: ").append(character.getAge()).append("岁\n");
                }
                appendIfNotNull(content, "角色描述", character.getDescription());
                appendIfNotNull(content, "角色背景", character.getBackground());
                
                if (character.getPersonality() != null && !character.getPersonality().isEmpty()) {
                    content.append("性格特征: ").append(String.join(", ", character.getPersonality())).append("\n");
                }
                if (character.getGoals() != null && !character.getGoals().isEmpty()) {
                    content.append("角色目标: ").append(String.join(", ", character.getGoals())).append("\n");
                }
                appendIfNotNull(content, "角色备注", character.getNotes());
                
                                 // 获取角色关系信息
                 try {
                     List<CharacterRelationship> relationships = 
                         characterRelationshipService.getByCharacterIds(Collections.singletonList(character.getId()));
                     if (relationships != null && !relationships.isEmpty()) {
                         content.append("角色关系: ");
                         List<String> relationshipDesc = new ArrayList<>();
                         for (CharacterRelationship rel : relationships) {
                             String desc = rel.getRelationshipType();
                             if (rel.getDescription() != null && !rel.getDescription().trim().isEmpty()) {
                                 desc += "(" + rel.getDescription() + ")";
                             }
                             relationshipDesc.add(desc);
                         }
                         content.append(String.join(", ", relationshipDesc)).append("\n");
                     }
                 } catch (Exception e) {
                     log.debug("获取角色关系信息失败: {}", e.getMessage());
                 }
                break;
                
            case "plot":
                Plot plot = (Plot) entity;
                content.append("=== 情节信息 ===\n");
                appendIfNotNull(content, "情节标题", plot.getTitle());
                appendIfNotNull(content, "情节类型", plot.getType());
                if (plot.getSortOrder() != null) {
                    content.append("情节序号: ").append(plot.getSortOrder()).append("\n");
                }
                appendIfNotNull(content, "情节状态", plot.getStatus());
                appendIfNotNull(content, "情节描述", plot.getDescription());
                
                // 尝试获取关联的章节信息
                if (plot.getChapterId() != null) {
                    try {
                        Chapter relatedChapter = chapterMapper.selectById(plot.getChapterId());
                        if (relatedChapter != null) {
                            content.append("所属章节: ").append(relatedChapter.getTitle()).append("\n");
                        }
                    } catch (Exception e) {
                        log.debug("获取关联章节信息失败: {}", e.getMessage());
                    }
                }
                break;
                
            case "world":
                World world = (World) entity;
                content.append("=== 世界观信息 ===\n");
                appendIfNotNull(content, "世界名称", world.getName());
                appendIfNotNull(content, "世界描述", world.getDescription());
                
                // 添加世界观元素信息
                if (world.getElements() != null && !world.getElements().isEmpty()) {
                    content.append("\n=== 世界观元素 ===\n");
                    for (Element element : world.getElements()) {
                        if (element != null) {
                            content.append("【").append(element.getType()).append("】");
                            if (element.getName() != null && !element.getName().trim().isEmpty()) {
                                content.append(" ").append(element.getName());
                            }
                            content.append("\n");
                            
                            if (element.getDescription() != null && !element.getDescription().trim().isEmpty()) {
                                content.append("描述: ").append(element.getDescription()).append("\n");
                            }
                            
                            if (element.getDetails() != null && !element.getDetails().trim().isEmpty()) {
                                content.append("详情: ").append(element.getDetails()).append("\n");
                            }
                            content.append("\n");
                        }
                    }
                }
                
                appendIfNotNull(content, "世界备注", world.getNotes());
                break;
        }
        
        return content.toString();
    }
    
    /**
     * 辅助方法：只有当值不为null且不为空时才添加到内容中
     */
    private void appendIfNotNull(StringBuilder content, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            content.append(label).append(": ").append(value.trim()).append("\n");
        }
    }

    private Map<String, Object> createBaseMetadata(Object entity, String entityType, Long version) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", entityType);
        metadata.put("version", version);
        metadata.put("status", "ACTIVE");
        metadata.put("lastModified", System.currentTimeMillis());
        
        switch (entityType) {
            case "project":
                Project project = (Project) entity;
                metadata.put("entityId", project.getId());
                metadata.put("projectId", project.getId());
                metadata.put("title", project.getTitle());
                metadata.put("genre", project.getGenre());
                metadata.put("style", project.getStyle());
                metadata.put("projectStatus", project.getStatus());
                if (project.getTags() != null) {
                    metadata.put("tags", project.getTags());
                }
                
                // 如果项目关联了世界观，触发世界观的项目关联同步
                if (project.getWorldId() != null) {
                    metadata.put("worldId", project.getWorldId());
                    // 异步触发世界观的项目关联同步
                    try {
                        triggerWorldProjectSync(project.getWorldId(), project.getId());
                    } catch (Exception e) {
                        log.debug("触发世界观项目关联同步失败: worldId={}, projectId={}", 
                                project.getWorldId(), project.getId(), e);
                    }
                }
                break;
                
            case "chapter":
                Chapter chapter = (Chapter) entity;
                metadata.put("entityId", chapter.getId());
                metadata.put("projectId", chapter.getProjectId());
                metadata.put("title", chapter.getTitle());
                metadata.put("chapterType", chapter.getType());
                metadata.put("sortOrder", chapter.getSortOrder());
                metadata.put("chapterStatus", chapter.getStatus());
                if (chapter.getWordCountGoal() != null) {
                    metadata.put("wordCountGoal", chapter.getWordCountGoal());
                }
                // 计算内容长度
                if (chapter.getContent() != null) {
                    metadata.put("contentLength", chapter.getContent().length());
                }
                break;
                
            case "character":
                com.soukon.novelEditorAi.entities.Character character = (com.soukon.novelEditorAi.entities.Character) entity;
                metadata.put("entityId", character.getId());
                metadata.put("projectId", character.getProjectId());
                metadata.put("name", character.getName());
                metadata.put("role", character.getRole());
                metadata.put("gender", character.getGender());
                metadata.put("age", character.getAge());
                if (character.getPersonality() != null) {
                    metadata.put("personality", character.getPersonality());
                }
                if (character.getGoals() != null) {
                    metadata.put("goals", character.getGoals());
                }
                break;
                
            case "plot":
                Plot plot = (Plot) entity;
                metadata.put("entityId", plot.getId());
                metadata.put("projectId", plot.getProjectId());
                metadata.put("title", plot.getTitle());
                metadata.put("plotType", plot.getType());
                metadata.put("sortOrder", plot.getSortOrder());
                metadata.put("plotStatus", plot.getStatus());
                metadata.put("chapterId", plot.getChapterId());
                break;
                
            case "world":
                World world = (World) entity;
                metadata.put("entityId", world.getId());
                metadata.put("name", world.getName());
                // World本身没有projectId，但需要支持项目关联查询
                
                // 添加元素类型信息到元数据
                if (world.getElements() != null && !world.getElements().isEmpty()) {
                    List<String> elementTypes = world.getElements().stream()
                            .filter(Objects::nonNull)
                            .map(Element::getType)
                            .filter(type -> type != null && !type.trim().isEmpty())
                            .distinct()
                            .collect(Collectors.toList());
                    if (!elementTypes.isEmpty()) {
                        metadata.put("elementTypes", elementTypes);
                    }
                    
                    List<String> elementNames = world.getElements().stream()
                            .filter(Objects::nonNull)
                            .map(Element::getName)
                            .filter(name -> name != null && !name.trim().isEmpty())
                            .collect(Collectors.toList());
                    if (!elementNames.isEmpty()) {
                        metadata.put("elementNames", elementNames);
                    }
                    
                    metadata.put("elementCount", world.getElements().size());
                }
                break;
                
            case "world-project":
                // 这是世界观的项目关联副本
                World worldCopy = (World) entity;
                metadata.put("entityId", worldCopy.getId());
                metadata.put("name", worldCopy.getName());
                
                // 这里需要从外部传入projectId，先用特殊方式处理
                // 实际的projectId会在调用时设置
                
                // 添加元素类型信息到元数据
                if (worldCopy.getElements() != null && !worldCopy.getElements().isEmpty()) {
                    List<String> elementTypes = worldCopy.getElements().stream()
                            .filter(Objects::nonNull)
                            .map(Element::getType)
                            .filter(type -> type != null && !type.trim().isEmpty())
                            .distinct()
                            .collect(Collectors.toList());
                    if (!elementTypes.isEmpty()) {
                        metadata.put("elementTypes", elementTypes);
                    }
                    
                    List<String> elementNames = worldCopy.getElements().stream()
                            .filter(Objects::nonNull)
                            .map(Element::getName)
                            .filter(name -> name != null && !name.trim().isEmpty())
                            .collect(Collectors.toList());
                    if (!elementNames.isEmpty()) {
                        metadata.put("elementNames", elementNames);
                    }
                    
                    metadata.put("elementCount", worldCopy.getElements().size());
                }
                break;
        }
        
        return metadata;
    }

    private List<Document> createChunkedDocuments(String content, String entityType, Long entityId, 
                                                  Long version, Map<String, Object> baseMetadata) {
        List<Document> documents = new ArrayList<>();
        
        // 简单的分片策略
        List<String> chunks = splitContent(content, chunkSize, chunkOverlap);
        
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunkMetadata = new HashMap<>(baseMetadata);
            chunkMetadata.put("chunkIndex", i);
            chunkMetadata.put("totalChunks", chunks.size());
            
            Document document = new Document(
                    entityType + "-" + entityId + "-v" + version + "-chunk-" + i,
                    chunks.get(i),
                    chunkMetadata
            );
            documents.add(document);
        }
        
        return documents;
    }

    private List<String> splitContent(String content, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        
        if (content.length() <= chunkSize) {
            chunks.add(content);
            return chunks;
        }
        
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            chunks.add(content.substring(start, end));
            
            // 如果这是最后一段（end已经到达内容末尾），直接退出
            if (end >= content.length()) {
                break;
            }
            
            // 计算下一个开始位置，确保有重叠但不会死循环
            start = end - chunkOverlap;
            
            // 确保start向前移动，防止死循环
            if (start <= 0) {
                start = end;
            }
        }
        
        return chunks;
    }

    private String hashContent(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    private int compareEventPriority(DataChangeEvent e1, DataChangeEvent e2) {
        Map<String, Integer> priority = Map.of(
                "DELETE", 3,
                "UPDATE", 2,
                "CREATE", 1
        );
        
        return Integer.compare(
                priority.getOrDefault(e2.getChangeType(), 0),
                priority.getOrDefault(e1.getChangeType(), 0)
        );
    }

    private void updateProjectVectorStatus(Long id, String status, String errorMessage) {
        UpdateWrapper<Project> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("vector_status", status)
                .set("vector_error_message", errorMessage);
        projectMapper.update(null, wrapper);
    }

    private void updateChapterVectorStatus(Long id, String status, String errorMessage) {
        UpdateWrapper<Chapter> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("vector_status", status)
                .set("vector_error_message", errorMessage);
        chapterMapper.update(null, wrapper);
    }

    private void updateCharacterVectorStatus(Long id, String status, String errorMessage) {
        UpdateWrapper<com.soukon.novelEditorAi.entities.Character> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("vector_status", status)
                .set("vector_error_message", errorMessage);
        characterMapper.update(null, wrapper);
    }

    private void updatePlotVectorStatus(Long id, String status, String errorMessage) {
        UpdateWrapper<Plot> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("vector_status", status)
                .set("vector_error_message", errorMessage);
        plotMapper.update(null, wrapper);
    }

    private void updateWorldVectorStatus(Long id, String status, String errorMessage) {
        UpdateWrapper<World> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("vector_status", status)
                .set("vector_error_message", errorMessage);
        worldMapper.update(null, wrapper);
    }

    private void updateVectorLastSync(String entityType, Long entityId) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (entityType) {
            case "project":
                UpdateWrapper<Project> projectWrapper = new UpdateWrapper<>();
                projectWrapper.eq("id", entityId).set("vector_last_sync", now);
                projectMapper.update(null, projectWrapper);
                break;
            case "chapter":
                UpdateWrapper<Chapter> chapterWrapper = new UpdateWrapper<>();
                chapterWrapper.eq("id", entityId).set("vector_last_sync", now);
                chapterMapper.update(null, chapterWrapper);
                break;
            case "character":
                UpdateWrapper<com.soukon.novelEditorAi.entities.Character> characterWrapper = new UpdateWrapper<>();
                characterWrapper.eq("id", entityId).set("vector_last_sync", now);
                characterMapper.update(null, characterWrapper);
                break;
            case "plot":
                UpdateWrapper<Plot> plotWrapper = new UpdateWrapper<>();
                plotWrapper.eq("id", entityId).set("vector_last_sync", now);
                plotMapper.update(null, plotWrapper);
                break;
            case "world":
                UpdateWrapper<World> worldWrapper = new UpdateWrapper<>();
                worldWrapper.eq("id", entityId).set("vector_last_sync", now);
                worldMapper.update(null, worldWrapper);
                break;
        }
    }
    
    /**
     * 触发世界观的项目关联同步
     * 为指定项目创建世界观文档副本，使其可以在项目范围内被搜索到
     */
    private void triggerWorldProjectSync(Long worldId, Long projectId) {
        try {
            log.debug("触发世界观项目关联同步: worldId={}, projectId={}", worldId, projectId);
            
            // 获取世界观实体
            World world = worldMapper.selectById(worldId);
            if (world == null) {
                log.warn("世界观不存在，跳过项目关联同步: worldId={}", worldId);
                return;
            }
            
            // 创建世界观的项目关联文档
            createWorldProjectDocument(world, projectId);
            
        } catch (Exception e) {
            log.error("世界观项目关联同步失败: worldId={}, projectId={}", worldId, projectId, e);
        }
    }
    
    /**
     * 创建世界观的项目关联文档
     */
    private void createWorldProjectDocument(World world, Long projectId) {
        try {
            String content = extractContent(world, "world");
            if (!StringUtils.hasText(content)) {
                log.warn("世界观内容为空，跳过项目关联文档创建: worldId={}", world.getId());
                return;
            }
            
            // 创建项目关联的元数据
            Map<String, Object> metadata = createBaseMetadata(world, "world-project", world.getVectorVersion());
            metadata.put("projectId", projectId); // 关键：设置项目ID
            metadata.put("worldId", world.getId());
            metadata.put("isWorldProjectCopy", true);
            
            // 创建文档ID，包含项目ID以区分不同项目的副本
            String documentId = "world-" + world.getId() + "-project-" + projectId + "-v" + world.getVectorVersion();
            
            // 如果内容较短，直接创建单个文档
            if (content.length() <= chunkSize) {
                Document document = new Document(documentId, content, metadata);
                vectorStore.add(Collections.singletonList(document));
                log.debug("创建世界观项目关联文档: worldId={}, projectId={}, docId={}", 
                        world.getId(), projectId, documentId);
            } else {
                // 内容较长，分片处理
                List<Document> documents = createWorldProjectChunkedDocuments(
                        content, world.getId(), projectId, world.getVectorVersion(), metadata);
                vectorStore.add(documents);
                log.debug("创建世界观项目关联分片文档: worldId={}, projectId={}, chunks={}", 
                        world.getId(), projectId, documents.size());
            }
            
        } catch (Exception e) {
            log.error("创建世界观项目关联文档失败: worldId={}, projectId={}", world.getId(), projectId, e);
        }
    }
    
    /**
     * 创建世界观项目关联的分片文档
     */
    private List<Document> createWorldProjectChunkedDocuments(String content, Long worldId, Long projectId, 
                                                             Long version, Map<String, Object> baseMetadata) {
        List<Document> documents = new ArrayList<>();
        
        List<String> chunks = splitContent(content, chunkSize, chunkOverlap);
        
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunkMetadata = new HashMap<>(baseMetadata);
            chunkMetadata.put("chunkIndex", i);
            chunkMetadata.put("totalChunks", chunks.size());
            
            String documentId = "world-" + worldId + "-project-" + projectId + "-v" + version + "-chunk-" + i;
            Document document = new Document(documentId, chunks.get(i), chunkMetadata);
            documents.add(document);
        }
        
        return documents;
    }
    
    /**
     * 删除世界观的项目关联文档
     */
    public void deleteWorldProjectDocument(Long worldId, Long projectId) {
        try {
            log.debug("删除世界观项目关联文档: worldId={}, projectId={}", worldId, projectId);
            
            // 构建删除条件：匹配worldId和projectId的文档
            String deletePattern = "world-" + worldId + "-project-" + projectId;
            
            // 删除所有相关文档（包括分片）
            vectorStore.delete(Collections.singletonList(deletePattern + "*"));
            
            log.debug("删除世界观项目关联文档完成: worldId={}, projectId={}", worldId, projectId);
            
        } catch (Exception e) {
            log.error("删除世界观项目关联文档失败: worldId={}, projectId={}", worldId, projectId, e);
        }
    }
} 