package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.model.chat.ProjectChatRequest;
import com.soukon.novelEditorAi.model.chat.ProjectChatContext;
import reactor.core.publisher.Flux;

/**
 * 项目对话服务接口
 */
public interface ProjectChatService {
    
    /**
     * 项目流式对话
     * @param projectId 项目ID
     * @param request 对话请求
     * @return 流式响应
     */
    Flux<String> streamChat(Long projectId, ProjectChatRequest request);
    
    /**
     * 获取项目对话上下文
     * @param projectId 项目ID
     * @param sessionId 会话ID（可选）
     * @return 对话上下文
     */
    ProjectChatContext getChatContext(Long projectId, String sessionId);
    
    /**
     * 清除项目对话上下文
     * @param projectId 项目ID
     * @param sessionId 会话ID（可选）
     */
    void clearChatContext(Long projectId, String sessionId);
    
    /**
     * 获取项目对话历史
     * @param projectId 项目ID
     * @param sessionId 会话ID（可选）
     * @param limit 限制数量
     * @return 对话历史
     */
    ProjectChatContext getChatHistory(Long projectId, String sessionId, Integer limit);
} 