package com.soukon.novelEditorAi.model.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板对话请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateChatRequest {
    
    /**
     * 模板ID
     */
    private Long templateId;
    
    /**
     * 用户消息
     */
    private String message;
    
    /**
     * 对话ID（用于维持上下文）
     */
    private String conversationId;
    
    /**
     * 最大检索结果数
     */
    private Integer maxResults;
    
    /**
     * 相似度阈值
     */
    private Float similarityThreshold;
} 