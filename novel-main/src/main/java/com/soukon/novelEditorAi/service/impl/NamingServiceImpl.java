package com.soukon.novelEditorAi.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.novelEditorAi.model.naming.NamingResponse;
import com.soukon.novelEditorAi.service.NamingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命名服务实现类
 */
@Service
public class NamingServiceImpl implements NamingService {
    
    // 系统提示词模板
    private static final String NAMING_SYSTEM_PROMPT = """
            你是一位专业的小说取名专家，帮助作者为作品、角色、章节取有吸引力且符合语境的名称。
            请根据用户提供的信息和要求，提供专业、有创意且富有文学性的名字选项。
            
            遵循以下规则:
            1. 提供符合中文文学习惯和语感的名称
            2. 名称应当简洁、有记忆点、符合作品类型和设定
            3. 避免过于晦涩或难以理解的名称
            4. 每次提供3-5个名称选项，并附带简短解释
            5. 名称应当匹配用户描述的风格、类型和主题
            
            必须严格遵守JSON格式，如果有疑问可以在notes字段里说明。
            返回格式必须是结构化的JSON格式，如下：
            {
                "names": [
                    {
                        "name": "名称1",
                        "explanation": "对这个名称的解释"
                    },
                    {
                        "name": "名称2",
                        "explanation": "对这个名称的解释"
                    }
                ],
                "notes": "其他说明或建议"
            }
            """;
    
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;


    public NamingServiceImpl( ObjectMapper objectMapper, ChatModel openAiChatModel) {
        this.chatClient = ChatClient.builder(openAiChatModel)
                // 实现 Chat Memory 的 Advisor
//                // 在使用 Chat Memory 时，需要指定对话 ID，以便 Spring AI 处理上下文。
//                .defaultAdvisors(
//                        new MessageChatMemoryAdvisor(new InMemoryChatMemory())
//                )
                // 实现 Logger 的 Advisor
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .topP(0.7)
                                .build()
                )
                .build();;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public NamingResponse generateNames(Map<String, Object> parameters) {
        // 生成用户提示词
        String userPrompt = buildUserPrompt(parameters);
        
        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(NAMING_SYSTEM_PROMPT));
        messages.add(new UserMessage(userPrompt));
        
        // 调用AI获取响应
        String response = chatClient.prompt(new Prompt(messages)).call().content();
        
        // 解析JSON响应
        try {
            return objectMapper.readValue(response, NamingResponse.class);
        } catch (JsonProcessingException e) {
            // 如果解析失败，返回错误信息
            throw new RuntimeException("解析AI响应失败: " + e.getMessage());
        }
    }
    
    @Override
    public Flux<String> generateNamesStream(Map<String, Object> parameters) {
        // 生成用户提示词
        String userPrompt = buildUserPrompt(parameters);
        
        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(NAMING_SYSTEM_PROMPT));
        messages.add(new UserMessage(userPrompt));
        
        // 调用AI获取流式响应
        return chatClient.prompt(new Prompt(messages)).stream().content();
    }
    
    /**
     * 构建用户提示词
     * @param parameters 参数映射
     * @return 格式化的用户提示词
     */
    private String buildUserPrompt(Map<String, Object> parameters) {
        StringBuilder userPrompt = new StringBuilder("请为以下内容生成合适的名称：\n");
        parameters.forEach((key, value) -> userPrompt.append(key).append(": ").append(value).append("\n"));
        return userPrompt.toString();
    }
} 