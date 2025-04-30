package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@Data
@TableName("chapters")
public class Chapter {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long projectId; // 项目ID
    private String title; // 章节标题
    private Integer sortOrder; // 排序顺序
    private String status; // 章节状态，枚举值：'draft'（草稿），'in-progress'（进行中），'completed'（已完成），'edited'（已编辑）
    private String summary; // 章节摘要
    private String notes; // 章节备注或背景信息
    private Long wordCountGoal; // 目标字数
    private Long wordCount; // 实际字数
    private String content; // 章节内容
    //  类型结构
    private String type;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; // 创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt; // 更新时间
} 