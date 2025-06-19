package com.soukon.novelEditorAi.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 检索增强生成(RAG)服务接口
 * 提供文档索引和检索功能
 */
public interface RagService {

    void test();
    void createOrUpdateDocument(String content, String documentId);

//   根据文档ID和查询文本检索相关文档
    List<Document> retrieveByDocumentId(String documentId, String query);
    /**
     * 为项目创建或更新索引
     * @param projectId 项目ID
     * @return 是否成功
     */
    boolean indexProject(Long projectId);
    
    /**
     * 为章节创建或更新索引
     * @param chapterId 章节ID
     * @return 是否成功
     */
    boolean indexChapter(Long chapterId);
    
    /**
     * 为角色创建或更新索引
     * @param characterId 角色ID
     * @return 是否成功
     */
    boolean indexCharacter(Long characterId);
    
    /**
     * 为世界观创建或更新索引
     * @param worldId 世界观ID
     * @return 是否成功
     */
    boolean indexWorld(Long worldId);
    
    /**
     * 基于项目ID和查询文本检索相关文档
     * @param projectId 项目ID
     * @param query 查询文本
     * @param maxResults 最大结果数
     * @return 相关文档列表
     */
    List<Document> retrieveByProjectId(Long projectId, String query, int maxResults);
    
    /**
     * 基于章节内容查询相关文档
     * @param chapterId 章节ID
     * @param maxResults 最大结果数
     * @return 相关文档列表
     */
    List<Document> retrieveRelevantForChapter(Long chapterId, int maxResults);
} 