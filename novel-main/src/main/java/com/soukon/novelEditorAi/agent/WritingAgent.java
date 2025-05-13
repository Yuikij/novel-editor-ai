
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
package com.soukon.novelEditorAi.agent;

import com.soukon.novelEditorAi.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

public class WritingAgent extends ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(WritingAgent.class);

    private final String nextStepPrompt = """


            完成当前步骤后，返回写作计划并执行下一步。

            终止条件：
                    - 计划中的所有步骤均已完成，或
            - 总字数达到{默认1000字，或由用户指定}，或
            - 上下文表明章节或场景已自然结束（例如，达到情节高潮或转折点）。

            如果继续，简要说明下一步的重点；如果停止，说明原因并总结已完成的内容。
                        为实现我的目标，下一步应该做什么？

                        重点：
                        1. 使用'get_text'操作获取页面内容，而不是滚动
                        2. 不用担心内容可见性或视口位置
                        3. 专注于基于文本的信息提取
                        4. 直接处理获取的文本数据
                        5. 重要：你必须在回复中使用至少一个工具才能取得进展！

                        考虑可见的内容和当前视口之外可能存在的内容。
                        有条理地行动 - 记住你的进度和迄今为止学到的知识。
                        """;


    private ToolCallbackProvider toolCallbackProvider;

    private ChatResponse response;

    private Prompt userPrompt;

    public WritingAgent(LlmService llmService, String name, String description, String systemPrompt
    ) {
        super(llmService);


    }

    public WritingAgent() {
        super(null);
    }

    @Override
    protected boolean think() {

        try {
            List<Message> messages = new ArrayList<>();
            addThinkPrompt(messages);

            ChatOptions chatOptions = ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build();
            Message nextStepMessage = getNextStepWithEnvMessage();
            messages.add(nextStepMessage);
            // in the

            log.debug("Messages prepared for the prompt: {}", messages);

            userPrompt = new Prompt(messages, chatOptions);

            response = llmService.getAgentChatClient(getPlanId())
                    .getChatClient()
                    .prompt(userPrompt)
                    .advisors(memoryAdvisor -> memoryAdvisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, getPlanId())
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
                    .call()
                    .chatResponse();

            List<ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();
            String responseByLLm = response.getResult().getOutput().getText();


            log.info(String.format("✨ %s's thoughts: %s", getName(), responseByLLm));
            log.info(String.format("🛠️ %s selected %d tools to use", getName(), toolCalls.size()));

            if (responseByLLm != null && !responseByLLm.isEmpty()) {
                log.info(String.format("💬 %s's response: %s", getName(), responseByLLm));
            }
            if (!toolCalls.isEmpty()) {
                log.info(String.format("🧰 Tools being prepared: %s",
                        toolCalls.stream().map(ToolCall::name).collect(Collectors.toList())));

            }


            return !toolCalls.isEmpty();
        } catch (Exception e) {
            log.error(String.format("🚨 Oops! The %s's thinking process hit a snag: %s", getName(), e.getMessage()));

            return false;
        }
    }

    @Override
    protected AgentExecResult act() {
        try {

            AgentExecResult agentExecResult = null;
            return agentExecResult;
        } catch (Exception e) {
            ToolCall toolCall = response.getResult().getOutput().getToolCalls().get(0);
            ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(toolCall.id(),
                    toolCall.name(), "Error: " + e.getMessage());
            ToolResponseMessage toolResponseMessage = new ToolResponseMessage(List.of(toolResponse), Map.of());
            llmService.getAgentChatClient(getPlanId()).getMemory().add(getPlanId(), toolResponseMessage);
            log.error(e.getMessage());


            return new AgentExecResult(e.getMessage(), AgentState.FAILED);
        }
    }

    @Override
    protected Message getNextStepWithEnvMessage() {
        String nextStepPrompt = """

                CURRENT STEP ENVIRONMENT STATUS:
                {current_step_env_data}

                """;
        nextStepPrompt = nextStepPrompt += this.nextStepPrompt;
        PromptTemplate promptTemplate = new PromptTemplate(nextStepPrompt);
        Message userMessage = promptTemplate.createMessage(getData());
        return userMessage;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    protected Message addThinkPrompt(List<Message> messages) {
        super.addThinkPrompt(messages);
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate("");
        Message systemMessage = promptTemplate.createMessage(getData());
        messages.add(systemMessage);
        return systemMessage;
    }


    public void addEnvData(String key, String value) {
        Map<String, Object> data = super.getData();
        if (data == null) {
            throw new IllegalStateException("Data map is null. Cannot add environment data.");
        }
        data.put(key, value);
    }


}
