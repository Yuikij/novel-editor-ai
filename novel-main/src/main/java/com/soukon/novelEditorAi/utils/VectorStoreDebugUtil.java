package com.soukon.novelEditorAi.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量存储调试工具类
 * 用于排查向量存储和检索的问题
 */
@Component
@Slf4j
public class VectorStoreDebugUtil {
    
    /**
     * 调试模板文档的存储和检索
     */
    public static void debugTemplateDocuments(VectorStore vectorStore, Long templateId) {
        log.info("=== 开始调试模板 {} 的向量存储 ===", templateId);
        
        try {
            // 尝试不同的过滤表达式，按成功率排序
            String[] filterExpressions = {
                "templateId == '" + templateId + "'",      // 单引号字符串 - 最有效
                "templateId == \"" + templateId + "\"",    // 双引号字符串 - 最有效
                "type == 'template'",                      // 类型过滤
                "type == \"template\"",                    // 类型过滤
                "templateId == " + templateId              // 数字类型 - 可能失败
            };
            
            for (String filter : filterExpressions) {
                try {
                    SearchRequest request = SearchRequest.builder()
                            .query("test")
                            .topK(5)
                            .similarityThreshold(0.0f)
                            .filterExpression(filter)
                            .build();
                    
                    List<Document> results = vectorStore.similaritySearch(request);
                    log.info("过滤条件 '{}' 找到 {} 个文档", filter, results.size());
                    
                    if (!results.isEmpty()) {
                        Document firstDoc = results.get(0);
                        log.info("第一个文档信息: id={}, metadata={}", 
                                firstDoc.getId(), firstDoc.getMetadata());
                    }
                    
                } catch (Exception e) {
                    log.warn("过滤条件 '{}' 执行失败: {}", filter, e.getMessage());
                }
            }
            
            // 尝试无过滤器的全量搜索
            try {
                SearchRequest noFilterRequest = SearchRequest.builder()
                        .query("test")
                        .topK(10)
                        .similarityThreshold(0.0f)
                        .build();
                
                List<Document> allResults = vectorStore.similaritySearch(noFilterRequest);
                log.info("无过滤器搜索找到 {} 个文档", allResults.size());
                
                // 找到相关的文档
                for (Document doc : allResults) {
                    if (doc.getId() != null && doc.getId().contains("template-" + templateId)) {
                        log.info("找到相关文档: id={}, metadata={}", 
                                doc.getId(), doc.getMetadata());
                    }
                }
                
            } catch (Exception e) {
                log.warn("无过滤器搜索失败: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("调试向量存储时发生错误: {}", e.getMessage(), e);
        }
        
        log.info("=== 模板 {} 向量存储调试完成 ===", templateId);
    }
} 