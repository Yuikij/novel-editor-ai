package com.soukon.novelEditorAi;

import com.soukon.novelEditorAi.agent.tool.ToolService;
import com.soukon.novelEditorAi.llm.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ToolTest {

    @Autowired
    private ChatModel openAiChatModel;

    @Autowired
    private ToolService toolService;

    @Autowired
    private LlmService llmService;


    @Test
    void contextLoads() {
        // 创建聊天客户端实例
// 设置系统提示信息，定义AI助手作为专业的室内设计顾问角色
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultTools(toolService)
//                .defaultSystem("你好啊")
                .build();
        System.out.println(chatClient.prompt("今天天气如何").call().content());
    }

    @Test
    void testWritingAgentToolCall() {
        // 模拟WritingAgent的工具调用场景
        String testPlanId = "1406277a-bf4b-4dc9-aee3-65c14a561293";
        
        try {
            // 使用LlmService的AgentChatClient（模拟WritingAgent的使用方式）
            ChatClient agentChatClient = llmService.getAgentChatClient(testPlanId).getChatClient();
            
            String testPrompt = """
                你是一位专业的小说创作助手。现在需要你执行以下任务：
                
                1. **必须严格按照以下参数调用latest_content_get工具**：
                   - 章节ID: "1922923956958334978"
                   - 字数: 500
                   - 计划ID: "%s"
                   
                2. 基于工具返回的信息进行简短回应
                
                重要提示：
                - 必须使用工具获取信息，不要跳过这一步
                - 请严格使用指定的章节ID："1922923956958334978"
                - 如果工具调用失败，请说明具体原因
                
                现在开始执行任务。
                """.formatted(testPlanId);
                
            String response = agentChatClient.prompt(testPrompt).call().content();
            System.out.println("=== 测试响应 ===");
            System.out.println(response);
            System.out.println("=== 测试完成 ===");
            
        } finally {
            // 清理测试数据
            llmService.removeAgentChatClient(testPlanId);
        }
    }
}
