package com.soukon.novelEditorAi.model.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模板向量化进度DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateVectorProgressDTO {
    
    /**
     * 模板ID
     */
    private Long templateId;
    
    /**
     * 模板名称
     */
    private String templateName;
    
    /**
     * 向量化状态：NOT_INDEXED(未索引), INDEXING(索引中), INDEXED(已索引), FAILED(索引失败)
     */
    private String vectorStatus;
    
    /**
     * 向量化进度百分比 (0-100)
     */
    private Integer vectorProgress;
    
    /**
     * 向量化开始时间
     */
    private LocalDateTime vectorStartTime;
    
    /**
     * 向量化完成时间
     */
    private LocalDateTime vectorEndTime;
    
    /**
     * 向量化错误信息
     */
    private String vectorErrorMessage;
    
    /**
     * 是否可以开始对话
     */
    private Boolean canChat;
} 