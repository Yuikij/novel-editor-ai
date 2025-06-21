package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.mapper.ProjectMapper;
import com.soukon.novelEditorAi.model.chat.ProjectChatRequest;
import com.soukon.novelEditorAi.model.chat.ProjectChatContext;
import com.soukon.novelEditorAi.model.chat.ProjectChatContext.ChatMessage;
import com.soukon.novelEditorAi.model.chat.ProjectChatContext.MessageRole;
import com.soukon.novelEditorAi.model.chat.ProjectChatContext.VectorSearchResult;
import com.soukon.novelEditorAi.service.ProjectChatService;
import com.soukon.novelEditorAi.service.ConsistentVectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 项目对话服务实现
 */
@Service
@Slf4j
public class ProjectChatServiceImpl implements ProjectChatService {

    @Autowired
    private LlmService llmService;
    
    @Autowired
    private ConsistentVectorSearchService vectorSearchService;
    
    @Autowired
    private ProjectMapper projectMapper;
    
    // 内存中的对话上下文缓存
    private final Map<String, ProjectChatContext> contextCache = new ConcurrentHashMap<>();
    
    @Override
    public Flux<String> streamChat(Long projectId, ProjectChatRequest request) {
        return Flux.create(sink -> {
            try {
                // 1. 验证项目存在
                Project project = projectMapper.selectById(projectId);
                if (project == null) {
                    sink.error(new IllegalArgumentException("项目不存在: " + projectId));
                    return;
                }
                
                // 2. 获取或创建对话上下文
                String contextKey = buildContextKey(projectId, request.getSessionId());
                ProjectChatContext context = getOrCreateContext(projectId, request.getSessionId());
                
                // 3. 执行向量检索（如果启用）
                List<VectorSearchResult> vectorResults = new ArrayList<>();
                if (Boolean.TRUE.equals(request.getEnableVectorSearch())) {
                    vectorResults = performVectorSearch(projectId, request);
                }
                
                // 4. 构建系统提示词
                String systemPrompt = buildSystemPrompt(project, context, vectorResults);
                
                // 5. 构建对话历史
                List<String> conversationHistory = buildConversationHistory(context);
                
                // 6. 添加用户消息到上下文
                context.addMessage(MessageRole.USER, request.getMessage(), vectorResults);
                
                // 7. 调用LLM流式生成
                StringBuilder assistantResponse = new StringBuilder();
                
                llmService.streamChat(
                    systemPrompt,
                    conversationHistory,
                    request.getMessage(),
                    request.getTemperature(),
                    request.getMaxTokens()
                ).subscribe(
                    chunk -> {
                        assistantResponse.append(chunk);
                        sink.next(chunk);
                    },
                    error -> {
                        log.error("LLM流式对话失败: projectId={}", projectId, error);
                        sink.error(error);
                    },
                    () -> {
                        // 8. 添加助手回复到上下文
                        context.addMessage(MessageRole.ASSISTANT, assistantResponse.toString());
                        
                        // 9. 更新上下文缓存
                        contextCache.put(contextKey, context);
                        
                        log.info("项目对话完成: projectId={}, turns={}", projectId, context.getTurnCount());
                        sink.complete();
                    }
                );
                
            } catch (Exception e) {
                log.error("项目流式对话启动失败: projectId={}", projectId, e);
                sink.error(e);
            }
        });
    }
    
    @Override
    public ProjectChatContext getChatContext(Long projectId, String sessionId) {
        String contextKey = buildContextKey(projectId, sessionId);
        return contextCache.get(contextKey);
    }
    
    @Override
    public void clearChatContext(Long projectId, String sessionId) {
        String contextKey = buildContextKey(projectId, sessionId);
        ProjectChatContext context = contextCache.get(contextKey);
        if (context != null) {
            context.clear();
            contextCache.put(contextKey, context);
        }
    }
    
    @Override
    public ProjectChatContext getChatHistory(Long projectId, String sessionId, Integer limit) {
        ProjectChatContext context = getChatContext(projectId, sessionId);
        if (context == null) {
            return null;
        }
        
        ProjectChatContext history = new ProjectChatContext();
        history.setProjectId(projectId);
        history.setSessionId(sessionId);
        history.setCreatedAt(context.getCreatedAt());
        history.setUpdatedAt(context.getUpdatedAt());
        history.setTurnCount(context.getTurnCount());
        history.setTotalTokens(context.getTotalTokens());
        history.setMessages(context.getRecentMessages(limit));
        
        return history;
    }
    
    /**
     * 构建上下文键
     */
    private String buildContextKey(Long projectId, String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            return projectId + ":" + sessionId;
        }
        return projectId + ":default";
    }
    
    /**
     * 获取或创建对话上下文
     */
    private ProjectChatContext getOrCreateContext(Long projectId, String sessionId) {
        String contextKey = buildContextKey(projectId, sessionId);
        return contextCache.computeIfAbsent(contextKey, key -> {
            ProjectChatContext context = new ProjectChatContext();
            context.setProjectId(projectId);
            context.setSessionId(sessionId);
            context.setCreatedAt(LocalDateTime.now());
            context.setUpdatedAt(LocalDateTime.now());
            return context;
        });
    }
    
    /**
     * 执行向量检索
     */
    private List<VectorSearchResult> performVectorSearch(Long projectId, ProjectChatRequest request) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(request.getMessage())
                    .topK(request.getMaxVectorResults())
//                    .similarityThreshold(request.getSimilarityThreshold())
                    .build();
            
            List<Document> documents = vectorSearchService.searchWithConsistency(searchRequest, projectId);
            
            return documents.stream()
                    .map(this::convertToVectorSearchResult)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("向量检索失败: projectId={}, query={}", projectId, request.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 转换文档为向量检索结果
     */
    private VectorSearchResult convertToVectorSearchResult(Document document) {
        VectorSearchResult result = new VectorSearchResult();
        result.setContent(document.getText());
        
        Map<String, Object> metadata = document.getMetadata();
        if (metadata != null) {
            result.setEntityType((String) metadata.get("entityType"));
            Object entityId = metadata.get("entityId");
            if (entityId != null) {
                result.setEntityId(Long.valueOf(entityId.toString()));
            }
            // 这里简化处理，实际可能需要从metadata中提取相似度分数
            result.setSimilarity(0.8f);
            result.setMetadata(metadata.toString());
        }
        
        return result;
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(Project project, ProjectChatContext context, List<VectorSearchResult> vectorResults) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个专业的小说创作助手，正在为以下项目提供帮助：\n\n");
        
        // 项目信息
        prompt.append("## 项目信息\n");
        prompt.append("标题：").append(project.getTitle()).append("\n");
        if (StringUtils.hasText(project.getSynopsis())) {
            prompt.append("简介：").append(project.getSynopsis()).append("\n");
        }
        if (StringUtils.hasText(project.getGenre())) {
            prompt.append("类型：").append(project.getGenre()).append("\n");
        }
        if (StringUtils.hasText(project.getStyle())) {
            prompt.append("风格：").append(project.getStyle()).append("\n");
        }
        
        // 向量检索结果
        if (!vectorResults.isEmpty()) {
            prompt.append("\n## 相关内容\n");
            prompt.append("以下是与用户问题相关的项目内容，请参考这些信息回答：\n\n");
            
            for (int i = 0; i < vectorResults.size(); i++) {
                VectorSearchResult result = vectorResults.get(i);
                prompt.append("### 相关内容 ").append(i + 1).append("（").append(result.getEntityType()).append("）\n");
                prompt.append(result.getContent()).append("\n\n");
            }
        }
        
        prompt.append("\n## 对话要求\n");
        prompt.append("1. 基于项目信息和相关内容回答用户问题，不可以回答项目无关的内容\n");
        prompt.append("2. 保持与项目设定的一致性\n");
        prompt.append("3. 提供有建设性的创作建议\n");
        prompt.append("4. 如果涉及创作内容，请保持创意性和原创性\n");
        prompt.append("5. 回答要专业、有用、易懂\n\n");
        
        return prompt.toString();
    }
    
    /**
     * 构建对话历史
     */
    private List<String> buildConversationHistory(ProjectChatContext context) {
        List<String> history = new ArrayList<>();
        
        // 获取最近的对话历史（限制数量避免token过多）
        List<ChatMessage> recentMessages = context.getRecentMessages(10);
        
        for (ChatMessage message : recentMessages) {
            String rolePrefix = message.getRole() == MessageRole.USER ? "用户: " : "助手: ";
            history.add(rolePrefix + message.getContent());
        }
        
        return history;
    }
} 