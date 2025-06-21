package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.events.DataChangeEvent;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 统一的向量同步服务接口
 * 负责处理所有实体的向量数据库同步
 */
public interface UnifiedVectorSyncService {
    
    /**
     * 处理数据变更事件
     * @param event 数据变更事件
     */
    void handleDataChangeEvent(DataChangeEvent event);
    
    /**
     * 同步实体到向量数据库
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param operation 操作类型：INDEX, UPDATE, DELETE
     * @param version 数据版本
     */
    void syncEntity(String entityType, Long entityId, String operation, Long version);
    
    /**
     * 检查内容是否发生变更
     * @param oldContent 旧内容
     * @param newContent 新内容
     * @return 是否发生变更
     */
    boolean isContentChanged(String oldContent, String newContent);
    
    /**
     * 获取实体的向量文档
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param version 版本号
     * @return 向量文档列表
     */
    List<Document> createVectorDocuments(String entityType, Long entityId, Long version);
    
    /**
     * 标记旧版本文档为已废弃
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param currentVersion 当前版本号
     */
    void markOldVersionsAsDeprecated(String entityType, Long entityId, Long version);
    
    /**
     * 删除实体的所有向量文档
     * @param entityType 实体类型
     * @param entityId 实体ID
     */
    void deleteEntityVectorDocuments(String entityType, Long entityId);
    
    /**
     * 更新实体的向量同步状态
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param status 状态
     * @param errorMessage 错误信息
     */
    void updateVectorStatus(String entityType, Long entityId, String status, String errorMessage);
    
    /**
     * 批量处理数据变更事件
     * @param events 事件列表
     */
    void processBatchEvents(List<DataChangeEvent> events);
    
    /**
     * 恢复失败的同步任务
     */
    void recoverFailedTasks();
    
    /**
     * 清理废弃的向量文档
     */
    void cleanupDeprecatedDocuments();
    
    /**
     * 删除世界观项目关联文档
     * @param worldId 世界观ID
     * @param projectId 项目ID
     */
    void deleteWorldProjectDocument(Long worldId, Long projectId);
} 