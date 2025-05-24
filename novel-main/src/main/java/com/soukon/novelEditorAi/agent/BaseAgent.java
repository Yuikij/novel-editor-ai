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
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An abstract base class for implementing AI agents that can execute multi-step tasks.
 * This class provides the core functionality for managing agent state, conversation flow,
 * and step-by-step execution of tasks.
 *
 * <p>
 * The agent supports a finite number of execution steps and includes mechanisms for:
 * <ul>
 * <li>State management (idle, running, finished)</li>
 * <li>Conversation tracking</li>
 * <li>Step limitation and monitoring</li>
 * <li>Thread-safe execution</li>
 * <li>Stuck-state detection and handling</li>
 * </ul>
 *
 * <p>
 * Implementing classes must define:
 * <ul>
 * <li>{@link #getName()} - Returns the agent's name</li>
 * <li>{@link #getDescription()} - Returns the agent's description</li>
 * <li>{@link #addThinkPrompt(List)} - Implements the thinking chain logic</li>
 * <li>{@link #step()} - Implements the core logic for each execution step</li>
 * </ul>
 *
 * @see AgentState
 * @see LlmService
 */
public abstract class BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(BaseAgent.class);

    String planId = null;

    private AgentState state = AgentState.NOT_STARTED;

    protected LlmService llmService;

    private int maxSteps;

    private int currentStep = 0;


    protected ChapterContentRequest chapterContentRequest;

    Map<String, Object> stepData;

    /**
     * 获取智能体的名称
     * <p>
     * 实现要求： 1. 返回一个简短但具有描述性的名称 2. 名称应该反映该智能体的主要功能或特性 3. 名称应该是唯一的，便于日志和调试
     * <p>
     * 示例实现： - ToolCallAgent 返回 "ToolCallAgent" - BrowserAgent 返回 "BrowserAgent"
     *
     * @return 智能体的名称
     */
    public abstract String getName();

    /**
     * 获取智能体的详细描述
     * <p>
     * 实现要求： 1. 返回对该智能体功能的详细描述 2. 描述应包含智能体的主要职责和能力 3. 应说明该智能体与其他智能体的区别
     * <p>
     * 示例实现： - ToolCallAgent: "负责管理和执行工具调用的智能体，支持多工具组合调用" - ReActAgent:
     * "实现思考(Reasoning)和行动(Acting)交替执行的智能体"
     *
     * @return 智能体的详细描述文本
     */
    public abstract String getDescription();

    /**
     * 添加思考提示到消息列表中，构建智能体的思考链
     * <p>
     * 实现要求： 1. 根据当前上下文和状态生成合适的系统提示词 2. 提示词应该指导智能体如何思考和决策 3. 可以递归地构建提示链，形成层次化的思考过程 4.
     * 返回添加的系统提示消息对象
     * <p>
     * 子类实现参考： 1. ReActAgent: 实现基础的思考-行动循环提示 2. ToolCallAgent: 添加工具选择和执行相关的提示
     *
     * @param messages 当前的消息列表，用于构建上下文
     * @return 添加的系统提示消息对象
     */
    protected Message addThinkPrompt(List<Message> messages) {
        // 获取操作系统信息
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");

        // 获取当前日期时间，格式为yyyy-MM-dd
        String currentDateTime = java.time.LocalDate.now().toString(); // 格式为yyyy-MM-dd

        String stepPrompt = """
                - SYSTEM INFORMATION:
                OS: %s %s (%s)
                                
                - Current Date:
                %s
                - 全局计划信息:
                {planStatus}
                                
                - 当前要做的步骤要求 :
                STEP {currentStepIndex} :{stepText}
                                
                - 当前步骤的上下文信息:
                {extraParams}
                                
                重要说明：
                1. 使用工具调用时，不需要额外的任何解释说明！
                2. 不要在工具调用前提供推理或描述！
                3. 专注于立即行动而非解释！
                                
                """.formatted(osName, osVersion, osArch, currentDateTime);

        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(stepPrompt);

        Message systemMessage = promptTemplate.createMessage();
        messages.add(systemMessage);
        return systemMessage;
    }

    /**
     * 获取下一步操作的提示消息
     * <p>
     * 实现要求： 1. 生成引导智能体执行下一步操作的提示消息 2. 提示内容应该基于当前执行状态和上下文 3. 消息应该清晰指导智能体要执行什么任务
     * <p>
     * 子类实现参考： 1. ToolCallAgent：返回工具选择和调用相关的提示 2. ReActAgent：返回思考或行动决策相关的提示
     *
     * @return 下一步操作的提示消息对象
     */
    protected abstract Message getNextStepWithEnvMessage();


    public BaseAgent(LlmService llmService, ChapterContentRequest request) {
        this.llmService = llmService;
        this.maxSteps = 5;
        this.chapterContentRequest = request;
        this.planId = request.getPlanContext().getPlanId();
    }

    public void run(Map<String, Object> stepData) {
        currentStep = 0;
        this.stepData = stepData;
        if (state != AgentState.IN_PROGRESS) {
            throw new IllegalStateException("Cannot run agent from state: " + state);
        }

        try {
            while (currentStep < maxSteps && !state.equals(AgentState.COMPLETED)) {
                log.info("正在运行计划 step {} of {}", currentStep + 1, maxSteps);
                currentStep++;
                step();
            }
            log.info("计划执行完成，当前状态: {} ，总步长：{}", state, currentStep - 1);
        } catch (Exception e) {
            log.error("Agent execution failed", e);
            throw e; // 重新抛出异常，让上层调用者知道发生了错误
        } finally {
            state = AgentState.COMPLETED; // Reset state after execution

        }
    }

    protected abstract AgentExecResult step();



    public void setState(AgentState state) {
        this.state = state;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public AgentState getState() {
        return state;
    }


    public static class AgentExecResult {

        private String result;

        private AgentState state;

        public AgentExecResult(String result, AgentState state) {
            this.result = result;
            this.state = state;
        }

        public String getResult() {
            return result;
        }

        public AgentState getState() {
            return state;
        }

    }

}
