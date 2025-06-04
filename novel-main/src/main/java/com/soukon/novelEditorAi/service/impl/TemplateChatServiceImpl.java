package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.enums.VectorStatus;
import com.soukon.novelEditorAi.mapper.TemplateMapper;
import com.soukon.novelEditorAi.model.template.TemplateChatRequest;
import com.soukon.novelEditorAi.model.template.TemplateChatContextVO;
import com.soukon.novelEditorAi.service.TemplateChatService;
import com.soukon.novelEditorAi.utils.QueryUtils;
import com.soukon.novelEditorAi.utils.VectorStoreDebugUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板对话服务实现类
 */
@Service
@Slf4j
public class TemplateChatServiceImpl implements TemplateChatService {

    @Autowired
    private TemplateMapper templateMapper;

    @Autowired
    private VectorStore vectorStore;

    private final ChatClient chatClient;

    @Value("${novel.template.chat.max-results:5}")
    private int defaultMaxResults;

    @Value("${novel.template.chat.similarity-threshold:0.0}")
    private float defaultSimilarityThreshold;

    @Value("${novel.template.chat.max-tokens:2000}")
    private int maxTokens;

    @Value("${novel.template.chat.temperature:0.7}")
    private double temperature;

    // 用于存储每个模板的对话上下文
    private final ConcurrentHashMap<String, String> conversationContexts = new ConcurrentHashMap<>();

    @Autowired
    public TemplateChatServiceImpl(@Qualifier("openAiChatModel")ChatModel chatModel) {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
                
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(memory).build(),
                        new SimpleLoggerAdvisor()
                )
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .maxTokens(maxTokens)
                                .temperature(temperature)
                                .build()
                )
                .build();
    }

    @Override
    public Result<String> chatWithTemplate(TemplateChatRequest request) {
        try {
            // 验证请求参数
            Result<Boolean> validationResult = validateChatRequest(request);
            if (validationResult.getCode() != 200) {
                return Result.error(validationResult.getMessage());
            }

            // 检查模板是否可以对话
            Result<Boolean> canChatResult = canChatWithTemplate(request.getTemplateId());
            if (canChatResult.getCode() != 200 || !canChatResult.getData()) {
                return Result.error("模板尚未完成向量化，无法进行对话");
            }

            // 获取模板信息
            LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
            QueryUtils.fillSelect(wrapper, Template.class, TemplateChatContextVO.class);
            wrapper.eq(Template::getId, request.getTemplateId());
            
            Template template = templateMapper.selectOne(wrapper);

            // 检索相关文档
            List<Document> relevantDocs = retrieveRelevantDocuments(request);

            // 构建上下文
            String context = buildContext(template, relevantDocs);

            // 构建系统提示词
            String systemPrompt = buildSystemPrompt(template, context);

            // 生成对话ID（如果没有提供）
            String conversationId = StringUtils.hasText(request.getConversationId()) 
                    ? request.getConversationId() 
                    : "template-" + request.getTemplateId() + "-" + System.currentTimeMillis();

            // 进行对话
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.getMessage())
                    .call()
                    .content();

            log.info("模板 {} 对话成功，对话ID: {}", request.getTemplateId(), conversationId);
            return Result.success("对话成功", response);

        } catch (Exception e) {
            log.error("模板对话失败: {}", e.getMessage(), e);
            return Result.error("对话失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatWithTemplateStream(TemplateChatRequest request) {
        return Flux.create(sink -> {
            try {
                // 验证请求参数
                Result<Boolean> validationResult = validateChatRequest(request);
                if (validationResult.getCode() != 200) {
                    sink.error(new RuntimeException(validationResult.getMessage()));
                    return;
                }

                // 检查模板是否可以对话
                Result<Boolean> canChatResult = canChatWithTemplate(request.getTemplateId());
                if (canChatResult.getCode() != 200 || !canChatResult.getData()) {
                    sink.error(new RuntimeException("模板尚未完成向量化，无法进行对话"));
                    return;
                }

                // 获取模板信息
                LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
                QueryUtils.fillSelect(wrapper, Template.class, TemplateChatContextVO.class);
                wrapper.eq(Template::getId, request.getTemplateId());
                
                Template template = templateMapper.selectOne(wrapper);

                // 检索相关文档
                List<Document> relevantDocs = retrieveRelevantDocuments(request);

                // 构建上下文
                String context = buildContext(template, relevantDocs);

                // 构建系统提示词
                String systemPrompt = buildSystemPrompt(template, context);

                // 生成对话ID（如果没有提供）
                String conversationId = StringUtils.hasText(request.getConversationId()) 
                        ? request.getConversationId() 
                        : "template-" + request.getTemplateId() + "-" + System.currentTimeMillis();

                // 进行流式对话
                Flux<String> responseFlux = chatClient.prompt()
                        .system(systemPrompt)
                        .user(request.getMessage())
                        .stream()
                        .content();

                responseFlux.subscribe(
                        sink::next,
                        sink::error,
                        sink::complete
                );

                log.info("模板 {} 流式对话开始，对话ID: {}", request.getTemplateId(), conversationId);

            } catch (Exception e) {
                log.error("模板流式对话失败: {}", e.getMessage(), e);
                sink.error(e);
            }
        });
    }

    @Override
    public Result<Boolean> canChatWithTemplate(Long templateId) {
        try {
            // 使用注解方案检查模板状态
            LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
            QueryUtils.fillSelect(wrapper, Template.class, TemplateChatContextVO.class);
            wrapper.eq(Template::getId, templateId);
            
            Template template = templateMapper.selectOne(wrapper);
            if (template == null) {
                return Result.error("模板不存在");
            }

            boolean canChat = VectorStatus.INDEXED.getCode().equals(template.getVectorStatus());
            return Result.success("查询成功", canChat);

        } catch (Exception e) {
            log.error("检查模板对话状态失败: {}", e.getMessage(), e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 验证对话请求参数
     */
    private Result<Boolean> validateChatRequest(TemplateChatRequest request) {
        if (request == null) {
            return Result.error("请求参数不能为空");
        }

        if (request.getTemplateId() == null) {
            return Result.error("模板ID不能为空");
        }

        if (!StringUtils.hasText(request.getMessage())) {
            return Result.error("消息内容不能为空");
        }

        return Result.success("验证通过", true);
    }

    /**
     * 检索相关文档
     */
    private List<Document> retrieveRelevantDocuments(TemplateChatRequest request) {
        try {
            int maxResults = request.getMaxResults() != null ? request.getMaxResults() : defaultMaxResults;
            float similarityThreshold = request.getSimilarityThreshold() != null ? request.getSimilarityThreshold() : defaultSimilarityThreshold;

            // 尝试多种过滤策略，按成功率排序
            String[] filterStrategies = {
                "type == 'template' && templateId == '" + request.getTemplateId() + "'",
            };

            List<Document> documents = null;
            String successfulFilter = null;

            // 尝试每种过滤策略
            for (String filterExpression : filterStrategies) {
                try {
                    log.debug("尝试过滤条件: {}", filterExpression);

                    SearchRequest searchRequest = SearchRequest.builder()
                            .query(request.getMessage())
                            .topK(maxResults)
//                            .similarityThreshold(similarityThreshold)
                            .filterExpression(filterExpression)
                            .build();

                    documents = vectorStore.similaritySearch(searchRequest);

                    if (!documents.isEmpty()) {
                        successfulFilter = filterExpression;
                        log.debug("过滤条件 '{}' 成功找到 {} 个文档", filterExpression, documents.size());
                        break;
                    } else {
                        log.debug("过滤条件 '{}' 未找到文档", filterExpression);
                    }

                } catch (Exception e) {
                    log.warn("过滤条件 '{}' 执行失败: {}", filterExpression, e.getMessage());
                }
            }

            // 如果所有过滤策略都失败，尝试无过滤器搜索并手动筛选
            if (documents == null || documents.isEmpty()) {
                log.warn("所有过滤策略都失败，尝试无过滤器搜索");
                
                try {
                    SearchRequest noFilterRequest = SearchRequest.builder()
                            .query(request.getMessage())
                            .topK(maxResults * 5) // 获取更多结果用于手动筛选
                            .similarityThreshold(similarityThreshold)
                            .build();

                    List<Document> allDocuments = vectorStore.similaritySearch(noFilterRequest);
                    
                    // 手动筛选属于该模板的文档
                    documents = allDocuments.stream()
                            .filter(doc -> {
                                Object templateIdObj = doc.getMetadata().get("templateId");
                                if (templateIdObj != null) {
                                    return templateIdObj.toString().equals(request.getTemplateId().toString());
                                }
                                // 也检查文档ID
                                return doc.getId() != null && 
                                       doc.getId().startsWith("template-" + request.getTemplateId() + "-");
                            })
                            .limit(maxResults)
                            .toList();
                    
                    if (!documents.isEmpty()) {
                        log.info("通过手动筛选找到 {} 个相关文档", documents.size());
                    }
                    
                } catch (Exception e) {
                    log.error("无过滤器搜索也失败: {}", e.getMessage());
                    documents = List.of();
                }
            }

            if (documents.isEmpty()) {
                log.warn("模板 {} 未找到任何相关文档，启动调试模式", request.getTemplateId());
                // 启动调试模式
                VectorStoreDebugUtil.debugTemplateDocuments(vectorStore, request.getTemplateId());
            } else {
                log.info("为模板 {} 检索到 {} 个相关文档，使用过滤条件: {}", 
                        request.getTemplateId(), documents.size(), successfulFilter);
            }

            return documents;

        } catch (Exception e) {
            log.error("检索相关文档失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 构建上下文
     */
    private String buildContext(Template template, List<Document> relevantDocs) {
        StringBuilder context = new StringBuilder();

        // 添加模板基本信息
        context.append("=== 模板信息 ===\n");
        context.append("模板名称: ").append(template.getName()).append("\n");
        if (StringUtils.hasText(template.getTags())) {
            context.append("模板标签: ").append(template.getTags()).append("\n");
        }
        context.append("\n");
        // 添加相关文档内容
        if (!relevantDocs.isEmpty()) {
            context.append("=== 相关内容 ===\n");
            for (Document doc : relevantDocs) {
                context.append(doc.getText()).append("\n\n");
            }
        }
        return context.toString();
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(Template template, String context) {
        StringBuilder systemPrompt = new StringBuilder();

        systemPrompt.append("你是一个基于模板内容的智能助手。你的任务是根据提供的模板内容回答用户的问题。\n\n");

        systemPrompt.append("模板背景信息:\n");
        systemPrompt.append("- 模板名称: ").append(template.getName()).append("\n");
        if (StringUtils.hasText(template.getTags())) {
            systemPrompt.append("- 模板标签: ").append(template.getTags()).append("\n");
        }
        systemPrompt.append("\n");

        systemPrompt.append("请遵循以下原则:\n");
        systemPrompt.append("1. 主要基于提供的模板内容来回答问题\n");
        systemPrompt.append("2. 如果问题超出模板内容范围，请明确说明并提供一般性建议\n");
        systemPrompt.append("3. 保持回答的准确性和相关性\n");
        systemPrompt.append("4. 如果需要，可以引用具体的模板片段\n");
        systemPrompt.append("5. 回答要简洁明了，避免冗长\n\n");

        systemPrompt.append("当前上下文信息:\n");
        systemPrompt.append(context);

        return systemPrompt.toString();
    }
} 