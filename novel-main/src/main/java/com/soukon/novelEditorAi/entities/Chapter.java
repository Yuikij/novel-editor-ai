package com.soukon.novelEditorAi.entities;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@Data
@TableName("chapters")
public class Chapter {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long projectId; // 项目ID
    @TableField(value = "template_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long templateId; // 模板ID
    private String title; // 章节标题
    private Integer sortOrder; // 排序顺序
    private String status; // 章节状态，枚举值：'draft'（草稿），'in-progress'（进行中），'completed'（已完成），'edited'（已编辑）
    private String summary; // 章节摘要
    private String notes; // 章节备注或背景信息
    private Long wordCountGoal; // 目标字数
    private Long wordCount; // 实际字数
    private String content; // 章节内容

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JSONObject historyContent; // 章节历史内容
    //  类型结构
    private String type;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; // 创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt; // 更新时间
    
    // Default constructor to initialize historyContent
    public Chapter() {
        this.historyContent = new JSONObject();
    }
    
    // Custom setter for historyContent to prevent null values
    public void setHistoryContent(JSONObject historyContent) {
        this.historyContent = historyContent == null ? new JSONObject() : historyContent;
    }
} 