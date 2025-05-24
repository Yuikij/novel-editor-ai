package com.soukon.novelEditorAi;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class NovelEditorAiApplicationTests {

    @Autowired
    private ChatModel openAiChatModel;
    @Test
    void contextLoads() {
        // 创建聊天客户端实例
// 设置系统提示信息，定义AI助手作为专业的室内设计顾问角色
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem("你是一位专业的室内设计顾问，精通各种装修风格、材料选择和空间布局。请基于提供的参考资料，为用户提供专业、详细且实用的建议。在回答时，请注意：\n" +
                        "1. 准确理解用户的具体需求\n" +
                        "2. 结合参考资料中的实际案例\n" +
                        "3. 提供专业的设计理念和原理解释\n" +
                        "4. 考虑实用性、美观性和成本效益\n" +
                        "5. 如有需要，可以提供替代方案")
                .build();

//// 构建查询扩展器
//// 用于生成多个相关的查询变体，以获得更全面的搜索结果
//        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
//                .chatClientBuilder(ChatClient.builder(openAiChatModel))
//                .includeOriginal(false) // 不包含原始查询
//                .numberOfQueries(3) // 生成3个查询变体
//                .build();
//
//// 执行查询扩展
//// 将原始问题"请提供几种推荐的装修风格?"扩展成多个相关查询
//        List<Query> queries = queryExpander.expand(
//                new Query("请提供几种推荐的装修风格?"));
//
//        System.out.println(queries);
    }

}
