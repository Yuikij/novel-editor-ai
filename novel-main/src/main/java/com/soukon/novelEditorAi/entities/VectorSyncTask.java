package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 向量同步任务实体
 */
@Data
@TableName("vector_sync_tasks")
public class VectorSyncTask {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 实体类型：project, chapter, character, plot, world
     */
    private String entityType;
    
    /**
     * 实体ID
     */
    private Long entityId;
    
    /**
     * 操作类型：INDEX, UPDATE, DELETE
     */
    private String operation;
    
    /**
     * 数据版本号
     */
    private Long version;
    
    /**
     * 任务状态：PENDING, PROCESSING, COMPLETED, FAILED
     */
    private String status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;
    
    public VectorSyncTask() {
        this.status = "PENDING";
        this.retryCount = 0;
    }
    
    public VectorSyncTask(String entityType, Long entityId, String operation, Long version) {
        this();
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
        this.version = version;
    }
} 