package com.soukon.novelEditorAi.entities;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@TableName(value = "projects", autoResultMap = true)
public class Project {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String title;
    private String genre;
    private String style;
    private String synopsis;
    @TableField(value = "template_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long templateId; // 模板ID

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private String targetAudience;
    //  类型结构
    private String type;
    private Long wordCountGoal;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> highlights;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> writingRequirements;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JSONObject draft;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JSONObject map;

    private String status;
    @TableField(value = "world_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long worldId;
    
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