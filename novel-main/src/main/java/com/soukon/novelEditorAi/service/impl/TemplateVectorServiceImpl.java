package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.enums.VectorStatus;
import com.soukon.novelEditorAi.mapper.TemplateMapper;
import com.soukon.novelEditorAi.model.template.TemplateVectorProgressDTO;
import com.soukon.novelEditorAi.model.template.TemplateBasicVO;
import com.soukon.novelEditorAi.model.template.TemplateExistenceVO;
import com.soukon.novelEditorAi.service.TemplateVectorService;
import com.soukon.novelEditorAi.utils.QueryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板向量化服务实现类
 */
@Service
@Slf4j
public class TemplateVectorServiceImpl implements TemplateVectorService {

    @Autowired
    private TemplateMapper templateMapper;

    @Autowired
    private VectorStore vectorStore;

    @Value("${novel.template.chunk-size:500}")
    private int chunkSize;

    @Value("${novel.template.chunk-overlap:100}")
    private int chunkOverlap;

    @Value("${novel.template.max-indexable-length:1000000}")
    private int maxIndexableLength;

    // 用于存储向量化进度的内存缓存
    private final Map<Long, TemplateVectorProgressDTO> progressCache = new ConcurrentHashMap<>();

    @Override
    public Result<TemplateVectorProgressDTO> getVectorProgress(Long templateId) {
        try {
            // 使用注解方案动态控制查询字段
            LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
            QueryUtils.fillSelect(wrapper, Template.class, TemplateBasicVO.class);
            wrapper.eq(Template::getId, templateId);
            
            Template template = templateMapper.selectOne(wrapper);
            if (template == null) {
                return Result.error("模板不存在");
            }

            // 先从缓存中获取
            TemplateVectorProgressDTO cachedProgress = progressCache.get(templateId);
            if (cachedProgress != null && VectorStatus.INDEXING.getCode().equals(cachedProgress.getVectorStatus())) {
                return Result.success("查询成功", cachedProgress);
            }

            // 从数据库获取最新状态
            TemplateVectorProgressDTO progress = TemplateVectorProgressDTO.builder()
                    .templateId(template.getId())
                    .templateName(template.getName())
                    .vectorStatus(template.getVectorStatus() != null ? template.getVectorStatus() : VectorStatus.NOT_INDEXED.getCode())
                    .vectorProgress(template.getVectorProgress() != null ? template.getVectorProgress() : 0)
                    .vectorStartTime(template.getVectorStartTime())
                    .vectorEndTime(template.getVectorEndTime())
                    .vectorErrorMessage(template.getVectorErrorMessage())
                    .canChat(VectorStatus.INDEXED.getCode().equals(template.getVectorStatus()))
                    .build();

            return Result.success("查询成功", progress);
        } catch (Exception e) {
            log.error("查询模板向量化进度失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Boolean> indexTemplate(Long templateId) {
        try {
            Template template = templateMapper.selectById(templateId);
            if (template == null) {
                return Result.error("模板不存在");
            }

            if (!StringUtils.hasText(template.getContent())) {
                return Result.error("模板内容为空，无法进行向量化");
            }

            // 检查是否正在索引中
            if (VectorStatus.INDEXING.getCode().equals(template.getVectorStatus())) {
                return Result.error("模板正在索引中，请稍后再试");
            }

            // 更新状态为索引中
            updateTemplateVectorStatus(templateId, VectorStatus.INDEXING, 0, LocalDateTime.now(), null, null);

            try {
                // 先删除现有索引
                deleteTemplateIndexInternal(templateId);

                // 执行向量化
                boolean success = performVectorization(template);

                if (success) {
                    // 更新状态为已索引
                    updateTemplateVectorStatus(templateId, VectorStatus.INDEXED, 100, null, LocalDateTime.now(), null);
                    progressCache.remove(templateId); // 清除缓存
                    log.info("模板 {} 向量化成功", templateId);
                    return Result.success("向量化成功", true);
                } else {
                    // 更新状态为失败
                    updateTemplateVectorStatus(templateId, VectorStatus.FAILED, 0, null, LocalDateTime.now(), "向量化处理失败");
                    progressCache.remove(templateId); // 清除缓存
                    return Result.error("向量化失败");
                }
            } catch (Exception e) {
                // 更新状态为失败
                updateTemplateVectorStatus(templateId, VectorStatus.FAILED, 0, null, LocalDateTime.now(), e.getMessage());
                progressCache.remove(templateId); // 清除缓存
                throw e;
            }
        } catch (Exception e) {
            log.error("模板向量化失败: {}", e.getMessage(), e);
            return Result.error("向量化失败: " + e.getMessage());
        }
    }

    @Override
    @Async
    public Result<Boolean> indexTemplateAsync(Long templateId) {
        return indexTemplate(templateId);
    }

    @Override
    @Transactional
    public Result<Boolean> deleteTemplateIndex(Long templateId) {
        try {
            // 使用注解方案检查模板是否存在
            LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
            QueryUtils.fillSelect(wrapper, Template.class, TemplateExistenceVO.class);
            wrapper.eq(Template::getId, templateId);
            
            Template template = templateMapper.selectOne(wrapper);
            if (template == null) {
                return Result.error("模板不存在");
            }

            // 删除向量索引
            deleteTemplateIndexInternal(templateId);

            // 重置向量化状态
            updateTemplateVectorStatus(templateId, VectorStatus.NOT_INDEXED, 0, null, null, null);
            progressCache.remove(templateId); // 清除缓存

            log.info("模板 {} 向量索引删除成功", templateId);
            return Result.success("删除成功", true);
        } catch (Exception e) {
            log.error("删除模板向量索引失败: {}", e.getMessage(), e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Boolean> batchIndexTemplates(List<Long> templateIds) {
        try {
            if (templateIds == null || templateIds.isEmpty()) {
                return Result.error("模板ID列表不能为空");
            }

            List<Long> successIds = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (Long templateId : templateIds) {
                try {
                    Result<Boolean> result = indexTemplate(templateId);
                    if (result.getCode() == 200) {
                        successIds.add(templateId);
                    } else {
                        errors.add("模板" + templateId + ": " + result.getMessage());
                    }
                } catch (Exception e) {
                    errors.add("模板" + templateId + ": " + e.getMessage());
                }
            }

            if (errors.isEmpty()) {
                return Result.success("批量向量化成功", true);
            } else if (successIds.isEmpty()) {
                return Result.error("批量向量化失败: " + String.join("; ", errors));
            } else {
                return Result.success("部分向量化成功，成功: " + successIds.size() + "个，失败: " + errors.size() + "个", true);
            }
        } catch (Exception e) {
            log.error("批量模板向量化失败: {}", e.getMessage(), e);
            return Result.error("批量向量化失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<TemplateVectorProgressDTO> getVectorProgressStream(Long templateId) {
        return Flux.interval(Duration.ofSeconds(1))
                .map(tick -> {
                    Result<TemplateVectorProgressDTO> result = getVectorProgress(templateId);
                    return result.getCode() == 200 ? result.getData() : null;
                })
                .filter(Objects::nonNull)
                .takeUntil(progress -> 
                    !VectorStatus.INDEXING.getCode().equals(progress.getVectorStatus())
                )
                .distinctUntilChanged();
    }

    /**
     * 执行向量化处理
     */
    private boolean performVectorization(Template template) {
        try {
            String content = template.getContent();
            
            // 限制处理的文本长度
            if (content.length() > maxIndexableLength) {
                log.warn("模板 {} 内容过长 ({}字符)，将只索引前 {} 字符",
                        template.getId(), content.length(), maxIndexableLength);
                content = content.substring(0, maxIndexableLength);
            }

            // 更新进度到20%
            updateProgressInCache(template.getId(), 20);

            // 将模板内容分块
            List<String> chunks = chunkText(content, chunkSize, chunkOverlap);
            List<Document> documents = new ArrayList<>();

            // 更新进度到40%
            updateProgressInCache(template.getId(), 40);

            // 为每个块创建文档
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = createTemplateMetadata(template);
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());

                Document doc = new Document(
                        "template-" + template.getId() + "-chunk-" + i,
                        chunks.get(i),
                        metadata
                );
                documents.add(doc);

                // 更新进度 (40% - 80%)
                int progress = 40 + (int) ((double) (i + 1) / chunks.size() * 40);
                updateProgressInCache(template.getId(), progress);
            }

            // 将文档添加到向量存储中
            vectorStore.add(documents);

            // 更新进度到100%
            updateProgressInCache(template.getId(), 100);

            log.info("已成功为模板 {} 创建索引，共 {} 个块", template.getId(), chunks.size());
            
            // 验证文档是否已正确存储
            try {
                String verifyFilter = "templateId == '" + template.getId() + "'";
                SearchRequest verifyRequest = SearchRequest.builder()
                        .query("test")
                        .topK(1)
                        .similarityThreshold(0.0f)
                        .filterExpression(verifyFilter)
                        .build();
                List<Document> verifyResults = vectorStore.similaritySearch(verifyRequest);
                log.debug("验证模板 {} 的文档存储，找到 {} 个文档", template.getId(), verifyResults.size());
                
                if (verifyResults.isEmpty()) {
                    log.warn("警告：模板 {} 向量化完成但未找到存储的文档", template.getId());
                }
            } catch (Exception verifyException) {
                log.warn("验证模板 {} 文档存储时出现异常: {}", template.getId(), verifyException.getMessage());
            }
            
            return true;
        } catch (Exception e) {
            log.error("模板向量化处理失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除模板的向量索引
     */
    private void deleteTemplateIndexInternal(Long templateId) {
        try {
            // 使用有效的过滤表达式（基于调试结果优化）
            String filterExpression = "templateId == '" + templateId + "'";
            vectorStore.delete(filterExpression);
            log.debug("已删除模板 {} 的向量索引，过滤条件: {}", templateId, filterExpression);
        } catch (Exception e) {
            log.warn("删除模板 {} 向量索引时出现异常: {}", templateId, e.getMessage());
            // 如果第一种方式失败，尝试双引号格式
            try {
                String fallbackFilter = "templateId == \"" + templateId + "\"";
                vectorStore.delete(fallbackFilter);
                log.debug("使用备用方式删除模板 {} 的向量索引", templateId);
            } catch (Exception fallbackException) {
                log.error("删除模板 {} 向量索引的备用方式也失败: {}", templateId, fallbackException.getMessage());
            }
        }
    }

    /**
     * 更新模板向量化状态
     */
    private void updateTemplateVectorStatus(Long templateId, VectorStatus status, Integer progress, 
                                            LocalDateTime startTime, LocalDateTime endTime, String errorMessage) {
        Template template = new Template();
        template.setId(templateId);
        template.setVectorStatus(status.getCode());
        template.setVectorProgress(progress);
        template.setVectorStartTime(startTime);
        template.setVectorEndTime(endTime);
        template.setVectorErrorMessage(errorMessage);
        
        templateMapper.updateById(template);
    }

    /**
     * 更新缓存中的进度
     */
    private void updateProgressInCache(Long templateId, Integer progress) {
        TemplateVectorProgressDTO cached = progressCache.get(templateId);
        if (cached == null) {
            // 使用注解方案获取模板基本信息
            LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
            QueryUtils.fillSelect(wrapper, Template.class, TemplateBasicVO.class);
            wrapper.eq(Template::getId, templateId);
            
            Template template = templateMapper.selectOne(wrapper);
            if (template != null) {
                cached = TemplateVectorProgressDTO.builder()
                        .templateId(templateId)
                        .templateName(template.getName())
                        .vectorStatus(VectorStatus.INDEXING.getCode())
                        .vectorProgress(progress)
                        .vectorStartTime(LocalDateTime.now())
                        .canChat(false)
                        .build();
            }
        } else {
            cached.setVectorProgress(progress);
        }
        
        if (cached != null) {
            progressCache.put(templateId, cached);
        }
    }

    /**
     * 将文本分块
     */
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 使用Spring AI的TokenTextSplitter进行分块
        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,
                overlap,
                overlap / 2,
                Integer.MAX_VALUE,
                true
        );

        // 创建临时文档进行分块
        Document tempDoc = new Document(text);
        List<Document> splitDocs = splitter.apply(Collections.singletonList(tempDoc));

        // 提取分块后的文本
        for (Document doc : splitDocs) {
            chunks.add(doc.getText());
        }

        return chunks;
    }

    /**
     * 创建模板元数据
     */
    private Map<String, Object> createTemplateMetadata(Template template) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "template-" + template.getId());
        metadata.put("templateId", template.getId()); // 作为Long类型存储
        metadata.put("type", "template");
        metadata.put("name", template.getName());
        metadata.put("tags", template.getTags());
        metadata.put("timestamp", System.currentTimeMillis());
        
        log.debug("创建模板元数据: templateId={}, type={}, name={}", 
                template.getId(), metadata.get("type"), template.getName());
        return metadata;
    }
} 