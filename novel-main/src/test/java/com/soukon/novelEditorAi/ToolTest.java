package com.soukon.novelEditorAi;

import com.soukon.novelEditorAi.agent.tool.ToolService;
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
}
