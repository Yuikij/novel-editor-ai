package com.soukon.novelEditorAi.model.chapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 章节内容生成请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContentRequest {
    
    /**
     * 章节ID
     */
    private Long chapterId;
    
    /**
     * 章节上下文
     */
    private ChapterContext chapterContext;
    
    /**
     * 章节标题
     */
    private String chapterTitle;
    
    /**
     * 生成提示词
     */
    private String prompt;
    
    /**
     * 章节大纲
     */
    private String chapterOutline;
    
    /**
     * 章节已有内容（用于续写）
     */
    private String existingContent;
    
    /**
     * 场景列表
     */
    private List<String> scenes;
    
    /**
     * 最大生成Token数
     */
    private Integer maxTokens;
    
    /**
     * 温度参数 (0.0-1.0)
     */
    private Float temperature;
    
    /**
     * 是否流式生成
     */
    private Boolean streamGeneration;
} 