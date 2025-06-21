package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 数据变更事件记录实体
 */
@Data
@TableName("data_change_events")
public class DataChangeEventRecord {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 实体类型
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;
    
    /**
     * 是否已处理
     */
    private Boolean processed;
    
    /**
     * 处理时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;
    
    public DataChangeEventRecord() {
        this.processed = false;
        this.eventTime = LocalDateTime.now();
    }
    
    public DataChangeEventRecord(String entityType, Long entityId, String changeType, Long version) {
        this();
        this.entityType = entityType;
        this.entityId = entityId;
        this.changeType = changeType;
        this.version = version;
    }
} 