package com.soukon.novelEditorAi.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据变更事件
 * 用于触发向量数据库的同步更新
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataChangeEvent {
    
    /**
     * 实体类型：project, chapter, character, plot, world
     */
    private String entityType;
    
    /**
     * 实体ID
     */
    private Long entityId;
    
    /**
     * 变更类型：CREATE, UPDATE, DELETE
     */
    private String changeType;
    
    /**
     * 数据版本号
     */
    private Long version;
    
    /**
     * 事件时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 项目ID（用于过滤和路由）
     */
    private Long projectId;
    
    /**
     * 是否需要立即处理（紧急更新）
     */
    private boolean urgent;
    
    public DataChangeEvent(String entityType, Long entityId, String changeType, Long version, LocalDateTime timestamp) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.changeType = changeType;
        this.version = version;
        this.timestamp = timestamp;
        this.urgent = false;
    }
    
    public DataChangeEvent(String entityType, Long entityId, String changeType, Long version, LocalDateTime timestamp, Long projectId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.changeType = changeType;
        this.version = version;
        this.timestamp = timestamp;
        this.projectId = projectId;
        this.urgent = false;
    }
} 