package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.*;
import com.soukon.novelEditorAi.mapper.*;
import com.soukon.novelEditorAi.service.ConsistentVectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 一致性向量搜索服务实现
 */
@Service
@Slf4j
public class ConsistentVectorSearchServiceImpl implements ConsistentVectorSearchService {

    @Autowired
    private VectorStore vectorStore;
    
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

    @Override
    public List<Document> searchWithConsistency(SearchRequest request, Long projectId) {
        try {
            // 1. 执行正常的向量搜索
            List<Document> results = vectorStore.similaritySearch(request);
            log.debug("向量搜索返回 {} 个结果", results.size());
            
            // 2. 过滤失效的文档
            List<Document> validResults = results.stream()
                    .filter(this::isDocumentValid)
                    .filter(this::documentEntityExists)
                    .collect(Collectors.toList());
            
            log.debug("过滤后有效结果 {} 个", validResults.size());
            
            // 3. 如果有效结果不足，尝试从MySQL获取补充结果
            if (validResults.size() < request.getTopK() && projectId != null) {
                List<Document> fallbackResults = getFallbackResults(request, projectId);
                validResults.addAll(fallbackResults);
                
                // 去重并限制结果数量
                validResults = validResults.stream()
                        .distinct()
                        .limit(request.getTopK())
                        .collect(Collectors.toList());
                
                log.debug("补充后最终结果 {} 个", validResults.size());
            }
            
            return validResults;
            
        } catch (Exception e) {
            log.error("一致性向量搜索失败", e);
            // 如果向量搜索失败，尝试纯MySQL备用方案
            if (projectId != null) {
                return getFallbackResults(request, projectId);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isDocumentValid(Document document) {
        try {
            Map<String, Object> metadata = document.getMetadata();
            if (metadata == null) {
                return false;
            }
            
            String entityType = (String) metadata.get("entityType");
            Object entityIdObj = metadata.get("entityId");
            Object versionObj = metadata.get("version");
            
            if (entityType == null || entityIdObj == null || versionObj == null) {
                log.debug("文档缺少必要的元数据: {}", metadata);
                return false;
            }
            
            Long entityId = convertToLong(entityIdObj);
            Long docVersion = convertToLong(versionObj);
            
            if (entityId == null || docVersion == null) {
                log.debug("元数据类型转换失败: entityId={}, version={}", entityIdObj, versionObj);
                return false;
            }
            
            // 检查实体当前版本
            Long currentVersion = getCurrentVersion(entityType, entityId);
            if (currentVersion == null) {
                log.debug("实体不存在: type={}, id={}", entityType, entityId);
                return false;
            }
            
            boolean isValid = docVersion.equals(currentVersion);
            if (!isValid) {
                log.debug("文档版本过期: type={}, id={}, docVersion={}, currentVersion={}", 
                        entityType, entityId, docVersion, currentVersion);
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.warn("验证文档有效性时出错: {}", document.getId(), e);
            return false;
        }
    }

    @Override
    public Long getCurrentVersion(String entityType, Long entityId) {
        try {
            switch (entityType) {
                case "project":
                    Project project = projectMapper.selectById(entityId);
                    return project != null ? project.getVectorVersion() : null;
                    
                case "chapter":
                    Chapter chapter = chapterMapper.selectById(entityId);
                    return chapter != null ? chapter.getVectorVersion() : null;
                    
                case "character":
                    com.soukon.novelEditorAi.entities.Character character = characterMapper.selectById(entityId);
                    return character != null ? character.getVectorVersion() : null;
                    
                case "plot":
                    Plot plot = plotMapper.selectById(entityId);
                    return plot != null ? plot.getVectorVersion() : null;
                    
                case "world":
                    World world = worldMapper.selectById(entityId);
                    return world != null ? world.getVectorVersion() : null;
                    
                default:
                    log.warn("未知的实体类型: {}", entityType);
                    return null;
            }
        } catch (Exception e) {
            log.error("获取实体版本失败: type={}, id={}", entityType, entityId, e);
            return null;
        }
    }

    @Override
    public List<Document> getFallbackResults(SearchRequest request, Long projectId) {
        try {
            List<Document> fallbackResults = new ArrayList<>();
            String query = request.getQuery();
            
            if (!StringUtils.hasText(query)) {
                return fallbackResults;
            }
            
            // 从各个实体类型中搜索匹配的内容
            fallbackResults.addAll(searchProjects(query, projectId));
            fallbackResults.addAll(searchChapters(query, projectId));
            fallbackResults.addAll(searchCharacters(query, projectId));
            fallbackResults.addAll(searchPlots(query, projectId));
            fallbackResults.addAll(searchWorlds(query, projectId));
            
            // 按相关性排序并限制数量
            return fallbackResults.stream()
                    .limit(Math.max(1, request.getTopK() / 2)) // 最多返回请求数量的一半作为补充
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("获取备用搜索结果失败: projectId={}", projectId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean entityExists(String entityType, Long entityId) {
        return getCurrentVersion(entityType, entityId) != null;
    }

    // 私有辅助方法

    private boolean documentEntityExists(Document document) {
        try {
            Map<String, Object> metadata = document.getMetadata();
            if (metadata == null) {
                return false;
            }
            
            String entityType = (String) metadata.get("entityType");
            Object entityIdObj = metadata.get("entityId");
            
            if (entityType == null || entityIdObj == null) {
                return false;
            }
            
            Long entityId = convertToLong(entityIdObj);
            if (entityId == null) {
                return false;
            }
            
            return entityExists(entityType, entityId);
            
        } catch (Exception e) {
            log.warn("检查文档实体存在性时出错: {}", document.getId(), e);
            return false;
        }
    }

    private Long convertToLong(Object obj) {
        if (obj == null) {
            return null;
        }
        
        if (obj instanceof Long) {
            return (Long) obj;
        }
        
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        
        if (obj instanceof String) {
            try {
                return Long.valueOf((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }

    private List<Document> searchProjects(String query, Long projectId) {
        try {
            List<Document> results = new ArrayList<>();
            
            // 如果指定了项目ID，只搜索该项目
            if (projectId != null) {
                Project project = projectMapper.selectById(projectId);
                if (project != null && contentMatches(project, query)) {
                    results.add(createDocumentFromProject(project));
                }
            }
            
            return results;
        } catch (Exception e) {
            log.warn("搜索项目失败: query={}, projectId={}", query, projectId, e);
            return Collections.emptyList();
        }
    }

    private List<Document> searchChapters(String query, Long projectId) {
        try {
            List<Document> results = new ArrayList<>();
            
            // 这里应该实现基于内容的搜索，为简化示例，我们只返回空列表
            // 实际实现中可以使用数据库的全文搜索功能
            
            return results;
        } catch (Exception e) {
            log.warn("搜索章节失败: query={}, projectId={}", query, projectId, e);
            return Collections.emptyList();
        }
    }

    private List<Document> searchCharacters(String query, Long projectId) {
        try {
            List<Document> results = new ArrayList<>();
            
            // 这里应该实现基于内容的搜索，为简化示例，我们只返回空列表
            // 实际实现中可以使用数据库的全文搜索功能
            
            return results;
        } catch (Exception e) {
            log.warn("搜索角色失败: query={}, projectId={}", query, projectId, e);
            return Collections.emptyList();
        }
    }

    private List<Document> searchPlots(String query, Long projectId) {
        try {
            List<Document> results = new ArrayList<>();
            
            // 这里应该实现基于内容的搜索，为简化示例，我们只返回空列表
            // 实际实现中可以使用数据库的全文搜索功能
            
            return results;
        } catch (Exception e) {
            log.warn("搜索情节失败: query={}, projectId={}", query, projectId, e);
            return Collections.emptyList();
        }
    }

    private List<Document> searchWorlds(String query, Long projectId) {
        try {
            List<Document> results = new ArrayList<>();
            
            if (projectId != null) {
                // 获取项目关联的世界观
                Project project = projectMapper.selectById(projectId);
                if (project != null && project.getWorldId() != null) {
                    World world = worldMapper.selectById(project.getWorldId());
                    if (world != null && contentMatches(world, query)) {
                        results.add(createDocumentFromWorld(world, projectId));
                    }
                }
            }
            
            return results;
        } catch (Exception e) {
            log.warn("搜索世界观失败: query={}, projectId={}", query, projectId, e);
            return Collections.emptyList();
        }
    }

    private boolean contentMatches(Project project, String query) {
        if (project == null || !StringUtils.hasText(query)) {
            return false;
        }
        
        String lowerQuery = query.toLowerCase();
        return (project.getTitle() != null && project.getTitle().toLowerCase().contains(lowerQuery)) ||
               (project.getSynopsis() != null && project.getSynopsis().toLowerCase().contains(lowerQuery)) ||
               (project.getGenre() != null && project.getGenre().toLowerCase().contains(lowerQuery)) ||
               (project.getStyle() != null && project.getStyle().toLowerCase().contains(lowerQuery));
    }
    
    private boolean contentMatches(World world, String query) {
        if (world == null || !StringUtils.hasText(query)) {
            return false;
        }
        
        String lowerQuery = query.toLowerCase();
        return (world.getName() != null && world.getName().toLowerCase().contains(lowerQuery)) ||
               (world.getDescription() != null && world.getDescription().toLowerCase().contains(lowerQuery)) ||
               (world.getNotes() != null && world.getNotes().toLowerCase().contains(lowerQuery)) ||
               (world.getElements() != null && world.getElements().stream()
                   .anyMatch(element -> element != null && 
                       ((element.getName() != null && element.getName().toLowerCase().contains(lowerQuery)) ||
                        (element.getType() != null && element.getType().toLowerCase().contains(lowerQuery)) ||
                        (element.getDescription() != null && element.getDescription().toLowerCase().contains(lowerQuery)))));
    }

    private Document createDocumentFromProject(Project project) {
        StringBuilder content = new StringBuilder();
        content.append("项目标题: ").append(project.getTitle()).append("\n");
        content.append("项目简介: ").append(project.getSynopsis()).append("\n");
        content.append("项目类型: ").append(project.getGenre()).append("\n");
        content.append("写作风格: ").append(project.getStyle()).append("\n");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", "project");
        metadata.put("entityId", project.getId());
        metadata.put("projectId", project.getId());
        metadata.put("version", project.getVectorVersion());
        metadata.put("title", project.getTitle());
        metadata.put("source", "fallback");
        
        return new Document(
                "project-" + project.getId() + "-fallback",
                content.toString(),
                metadata
        );
    }
    
    private Document createDocumentFromWorld(World world, Long projectId) {
        StringBuilder content = new StringBuilder();
        content.append("=== 世界观信息 ===\n");
        content.append("世界名称: ").append(world.getName()).append("\n");
        content.append("世界描述: ").append(world.getDescription()).append("\n");
        
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
        
        if (world.getNotes() != null && !world.getNotes().trim().isEmpty()) {
            content.append("世界备注: ").append(world.getNotes()).append("\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", "world");
        metadata.put("entityId", world.getId());
        metadata.put("projectId", projectId); // 重要：设置项目ID以支持项目隔离
        metadata.put("worldId", world.getId());
        metadata.put("name", world.getName());
        metadata.put("version", world.getVectorVersion());
        metadata.put("source", "fallback");
        metadata.put("isWorldProjectCopy", true);
        
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
        
        return new Document(
                "world-" + world.getId() + "-project-" + projectId + "-fallback",
                content.toString(),
                metadata
        );
    }
} 