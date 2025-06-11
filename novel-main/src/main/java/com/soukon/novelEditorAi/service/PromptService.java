package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 提示词服务接口，负责生成AI交互所需的提示词
 */
public interface PromptService {
    
    /**
     * 构建推理阶段的提示词（用于分析和规划）
     * @param request 章节内容请求
     * @return 推理阶段的提示词消息列表
     */
    List<Message> buildReasoningPrompt(ChapterContentRequest request);



    /**
     * 构建执行阶段的提示词（用于实际创作）
     * @param request 章节内容请求
     * @param reasoningResult 推理阶段的结果
     * @return 执行阶段的提示词消息列表
     */
    List<Message> buildActingPrompt(ChapterContentRequest request, String reasoningResult);
}
