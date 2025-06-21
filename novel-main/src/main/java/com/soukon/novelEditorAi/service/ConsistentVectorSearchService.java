package com.soukon.novelEditorAi.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;

/**
 * 一致性向量搜索服务
 * 确保查询时的数据一致性，过滤失效数据
 */
public interface ConsistentVectorSearchService {
    
    /**
     * 执行一致性向量搜索
     * @param request 搜索请求
     * @param projectId 项目ID（可选，用于过滤）
     * @return 有效的搜索结果
     */
    List<Document> searchWithConsistency(SearchRequest request, Long projectId);
    
    /**
     * 验证文档是否有效（版本是否匹配）
     * @param document 文档
     * @return 是否有效
     */
    boolean isDocumentValid(Document document);
    
    /**
     * 获取实体的当前版本号
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 当前版本号，如果实体不存在返回null
     */
    Long getCurrentVersion(String entityType, Long entityId);
    
    /**
     * 从MySQL获取备用搜索结果（当向量搜索结果不足时）
     * @param request 搜索请求
     * @param projectId 项目ID
     * @return 备用搜索结果
     */
    List<Document> getFallbackResults(SearchRequest request, Long projectId);
    
    /**
     * 检查实体是否仍然存在
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 是否存在
     */
    boolean entityExists(String entityType, Long entityId);
} 