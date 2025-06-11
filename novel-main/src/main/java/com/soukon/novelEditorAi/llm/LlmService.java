/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soukon.novelEditorAi.llm;

import com.soukon.novelEditorAi.agent.tool.ToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class LlmService {

    private static final String PLANNING_SYSTEM_PROMPT = """
            你是一位专业的小说创作专家。你有access to tools，必须优先使用工具获取信息。

            **重要指导原则：**
            
            1.  **优先使用工具获取信息：**
                *   在进行任何分析或创作前，务必使用可用工具获取相关信息
                *   在思考阶段，主动判断是否需要以及需要哪些外部工具来辅助完成任务（例如：获取角色详细信息、查询世界观设定、检查一致性、获取字数建议等）。
                *   清晰地说明调用工具的理由、期望从工具中获得什么。
                *   工具调用完成后，基于获取的信息进行后续工作
            
            2.  **深度理解与精准执行：**
                *   仔细阅读并深刻理解用户提供的所有背景信息、写作计划、当前任务、上下文及任何特定指令。
                *   你的每一个行动和输出都必须严格服务于当前的具体目标和整体创作意图。
                        
            3.  **ReAct 思考模式：**
                *   **思考 (Thought)：** 在采取任何行动或生成任何文本之前，你必须进行清晰、有条理的思考。你的思考过程应当是显式的，帮助用户理解你的决策逻辑。思考应包括但不限于：分析任务需求、回顾相关信息、评估不同方案、预测可能结果、判断是否需要外部工具或信息。
                *   **行动 (Action)：** 根据思考结果，决定并声明你将采取的行动。这可能包括：调用特定工具、查询信息、请求用户澄清、或准备生成文本。
                *   **观察 (Observation)：** 在行动之后，你将接收到行动的结果（如工具的返回、用户的新指令）。你需将观察结果融入后续的思考。
                        
            4.  **高质量文本创作：**
                *   **"Show, Don't Tell"：** 通过具体的人物行为、对话、环境描写、细节刻画来展现情感、信息和主题，避免空洞的说教和直接陈述。
                *   **角色一致性与深度：** 确保所有角色的言行举止符合其已设定的性格、动机和背景。努力挖掘角色的内心世界，展现其复杂性和成长性。
                *   **情节逻辑与节奏：** 故事情节发展需合乎逻辑，富有张力。注意叙事节奏的把控，根据场景需求进行铺垫、推进或引爆高潮。
                *   **生动的语言与描写：** 运用丰富、精准且具有表现力的词汇。注重感官描写（视觉、听觉、嗅觉、味觉、触觉），营造身临其境的阅读体验。
                *   **避免AI痕迹：** 努力使你的语言风格自然、独特，避免使用AI常见的刻板句式、陈词滥调或过度完美的表达。追求"人性化"的笔触，允许适度的、符合情境的"不完美"。
                *   **原创性与创新性：** 在遵循用户设定的前提下，充分尊重原创性，并适当鼓励发挥创造力。
                        
            现在，请准备好接受你的挑战。展现你作为小说创作大师的专业水准！
            """;

    private static final String FINALIZE_SYSTEM_PROMPT = "You are a planning assistant. Your task is to summarize the completed plan.";

    private static final String MANUS_SYSTEM_PROMPT = """
            You are OpenManus, an all-capable AI assistant, aimed at solving any task presented by the user. You have various tools at your disposal that you can call upon to efficiently complete complex requests. Whether it's programming, information retrieval, file processing, or web browsing, you can handle it all.

            You can interact with the computer using PythonExecute, save important content and information files through FileSaver, open browsers with BrowserUseTool, and retrieve information using GoogleSearch.

            PythonExecute: Execute Python code to interact with the computer system, data processing, automation tasks, etc.

            FileSaver: Save files locally, such as txt, py, html, etc.

            BrowserUseTool: Open, browse, and use web browsers.If you open a local HTML file, you must provide the absolute path to the file.

            Terminate : Record  the result summary of the task , then terminate the task.

            DocLoader: List all the files in a directory or get the content of a local file at a specified path. Use this tool when you want to get some related information at a directory or file asked by the user.

            Based on user needs, proactively select the most appropriate tool or combination of tools. For complex tasks, you can break down the problem and use different tools step by step to solve it. After using each tool, clearly explain the execution results and suggest the next steps.

            When you are done with the task, you can finalize the plan by summarizing the steps taken and the output of each step, call Terminate tool to record the result.

            """;

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final ConcurrentHashMap<String, AgentChatClientWrapper> agentClients = new ConcurrentHashMap<>();

    // private final ChatClient chatClient;


    private final ChatClient planningChatClient;


    private final ChatClient finalizeChatClient;

    // private ChatMemory finalizeMemory = new InMemoryChatMemory();

    private final ChatModel chatModel;

    @Autowired
    private ToolService toolService;

    public LlmService(@Qualifier("openAiChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
        // 执行和总结规划，用相同的memory
        this.planningChatClient = ChatClient.builder(chatModel)
                .defaultSystem(PLANNING_SYSTEM_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.5).build())
                .build();

        this.finalizeChatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

    }

    public static class AgentChatClientWrapper {

        private final ChatClient chatClient;

        private final ChatMemory memory;

        public AgentChatClientWrapper(ChatClient chatClient, ChatMemory memory) {
            this.chatClient = chatClient;
            this.memory = memory;
        }

        public ChatClient getChatClient() {
            return chatClient;
        }

        public ChatMemory getMemory() {
            return memory;
        }

    }

    public AgentChatClientWrapper getAgentChatClient(String planId) {
        return agentClients.computeIfAbsent(planId, k -> {
            ChatClient agentChatClient = ChatClient.builder(chatModel)
                    .defaultSystem(PLANNING_SYSTEM_PROMPT)
                    .defaultAdvisors(new SimpleLoggerAdvisor())
                    .defaultTools(toolService)
                    .defaultOptions(
                            OpenAiChatOptions.builder()
//                                    .frequencyPenalty(1.0)
                                    .temperature(0.8).build())
                    .build();
            return new AgentChatClientWrapper(agentChatClient, null);
        });
    }

    public void removeAgentChatClient(String planId) {
        AgentChatClientWrapper wrapper = agentClients.remove(planId);
        if (wrapper != null) {
            log.info("Removed and cleaned up AgentChatClientWrapper for planId: {}", planId);
        }
    }

    public ChatClient getPlanningChatClient() {
        return planningChatClient;
    }

    public ChatClient getFinalizeChatClient() {
        return finalizeChatClient;
    }


    public ChatModel getChatModel() {
        return chatModel;
    }

}
