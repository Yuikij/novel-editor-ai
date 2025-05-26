package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.model.template.TemplateChatRequest;
import reactor.core.publisher.Flux;

/**
 * 模板对话服务接口
 */
public interface TemplateChatService {
    
    /**
     * 与模板进行对话
     * @param request 对话请求
     * @return 对话响应
     */
    Result<String> chatWithTemplate(TemplateChatRequest request);
    
    /**
     * 与模板进行流式对话
     * @param request 对话请求
     * @return 流式响应
     */
    Flux<String> chatWithTemplateStream(TemplateChatRequest request);
    
    /**
     * 检查模板是否可以对话
     * @param templateId 模板ID
     * @return 是否可以对话
     */
    Result<Boolean> canChatWithTemplate(Long templateId);
} 