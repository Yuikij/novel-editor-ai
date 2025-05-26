package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 章节列表DTO - 不包含content和historyContent字段，用于列表查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterListDTO {
    
    /**
     * 章节ID
     */
    private Long id;
    
    /**
     * 项目ID
     */
    private Long projectId;
    
    /**
     * 模板ID
     */
    private Long templateId;
    
    /**
     * 章节标题
     */
    private String title;
    
    /**
     * 排序顺序
     */
    private Integer sortOrder;
    
    /**
     * 章节状态
     */
    private String status;
    
    /**
     * 章节摘要
     */
    private String summary;
    
    /**
     * 章节备注或背景信息
     */
    private String notes;
    
    /**
     * 目标字数
     */
    private Long wordCountGoal;
    
    /**
     * 实际字数
     */
    private Long wordCount;
    
    /**
     * 类型结构
     */
    private String type;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
} 