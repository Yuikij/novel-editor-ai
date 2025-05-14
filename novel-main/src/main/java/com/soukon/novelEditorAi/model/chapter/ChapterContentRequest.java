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

    private PlanContext planContext;

    /**
     * 章节ID
     */
    private Long chapterId;
    
    /**
     * 章节上下文
     */
    private ChapterContext chapterContext;


    /**
     * 上下文
     */
    private String context;


    /**
     * 总计划
     */
    private String plan;

    /**
     * 目标情节
     */
    private String globalContext;

    /**
     * 最大生成Token数
     */
    private Integer maxTokens;
    
    /**
     * 温度参数 (0.0-1.0)
     */
    private Float temperature;

    
    /**
     * 提示建议（如：写作风格、情感基调等建议）
     */
    private String promptSuggestion;
    
    /**
     * 字数建议（如：希望生成的章节字数）
     */
    private Integer wordCountSuggestion;
} 