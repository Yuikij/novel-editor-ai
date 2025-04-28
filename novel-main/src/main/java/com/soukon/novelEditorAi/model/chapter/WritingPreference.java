package com.soukon.novelEditorAi.model.chapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 写作偏好设置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingPreference {

    /**
     * 写作风格（如：幽默、严肃、抒情等）
     */
    private String writingStyle;
    
    /**
     * 语气（如：正式、轻松、讽刺等）
     */
    private String tone;
    
    /**
     * 叙述视角（如：第一人称、第三人称等）
     */
    private String narrativePerspective;
    
    /**
     * 情感基调（如：欢快、悲伤、紧张等）
     */
    private String emotionalTone;
    
    /**
     * 文字复杂度（简单/中等/复杂）
     */
    private String complexity;
    
    /**
     * 是否使用修辞手法（如：比喻、拟人等）
     */
    private boolean useRhetoricalDevices;
    
    /**
     * 句子长度偏好（短句/中等/长句）
     */
    private String sentenceLength;
    
    /**
     * 对话比例（少/中/多）
     */
    private String dialogueFrequency;
} 