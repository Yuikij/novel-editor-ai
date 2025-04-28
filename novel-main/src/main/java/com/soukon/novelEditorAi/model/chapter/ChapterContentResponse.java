package com.soukon.novelEditorAi.model.chapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 章节内容生成响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContentResponse {
    /**
     * 章节ID
     */
    private Long chapterId;
    
    /**
     * 项目ID
     */
    private Long projectId;
    
    /**
     * 生成的章节内容
     */
    private String content;
    
    /**
     * 生成的内容字数
     */
    private Integer wordCount;
    
    /**
     * 生成耗时（毫秒）
     */
    private Long generationTime;
    
    /**
     * 是否为补全内容
     */
    private Boolean isComplete;
} 