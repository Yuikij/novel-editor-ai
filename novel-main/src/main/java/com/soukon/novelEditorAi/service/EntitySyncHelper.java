package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.events.DataChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 实体同步助手
 * 提供便捷的方法来触发向量数据库同步
 */
@Service
@Slf4j
public class EntitySyncHelper {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private UnifiedVectorSyncService vectorSyncService;

    /**
     * 触发实体创建同步
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param projectId 项目ID（可选）
     * @param urgent 是否紧急处理
     */
    public void triggerCreate(String entityType, Long entityId, Long projectId, boolean urgent) {
        triggerSync(entityType, entityId, "CREATE", 1L, projectId, urgent);
    }

    /**
     * 触发实体更新同步
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param version 版本号
     * @param projectId 项目ID（可选）
     * @param urgent 是否紧急处理
     */
    public void triggerUpdate(String entityType, Long entityId, Long version, Long projectId, boolean urgent) {
        triggerSync(entityType, entityId, "UPDATE", version, projectId, urgent);
    }

    /**
     * 触发实体删除同步
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param projectId 项目ID（可选）
     * @param urgent 是否紧急处理
     */
    public void triggerDelete(String entityType, Long entityId, Long projectId, boolean urgent) {
        triggerSync(entityType, entityId, "DELETE", null, projectId, urgent);
    }

    /**
     * 检查内容是否发生变更
     * @param oldContent 旧内容
     * @param newContent 新内容
     * @return 是否发生变更
     */
    public boolean isContentChanged(String oldContent, String newContent) {
        return vectorSyncService.isContentChanged(oldContent, newContent);
    }

    /**
     * 立即同步实体（同步方式）
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param operation 操作类型
     * @param version 版本号
     */
    public void syncImmediately(String entityType, Long entityId, String operation, Long version) {
        try {
            vectorSyncService.syncEntity(entityType, entityId, operation, version);
        } catch (Exception e) {
            log.error("立即同步实体失败: type={}, id={}, operation={}", entityType, entityId, operation, e);
        }
    }

    // 私有方法

    private void triggerSync(String entityType, Long entityId, String changeType, Long version, Long projectId, boolean urgent) {
        try {
            DataChangeEvent event = new DataChangeEvent(
                    entityType, 
                    entityId, 
                    changeType, 
                    version != null ? version : 1L, 
                    LocalDateTime.now(), 
                    projectId
            );
            event.setUrgent(urgent);
            
            // 发布事件，异步处理
            eventPublisher.publishEvent(event);
            
            log.debug("已触发向量同步事件: type={}, id={}, changeType={}, urgent={}", 
                    entityType, entityId, changeType, urgent);
            
        } catch (Exception e) {
            log.error("触发向量同步事件失败: type={}, id={}, changeType={}", 
                    entityType, entityId, changeType, e);
        }
    }
} 