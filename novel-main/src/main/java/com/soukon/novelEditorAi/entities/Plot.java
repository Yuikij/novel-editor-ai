package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "plots", autoResultMap = true)
public class Plot {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;
    private Long chapterId;
    @TableField(value = "template_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long templateId; // 模板ID
    private String title;
    private String description;
    private Integer sortOrder;
    //  类型结构
    private String type;
    private String status;
    private Integer completionPercentage;
    //  目标字数
    private Integer wordCountGoal;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> characterIds;
    //  相关条目id
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> itemIds;

    // 向量化相关字段
    private String vectorStatus;
    private Long vectorVersion;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime vectorLastSync;
    private String vectorErrorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
} 