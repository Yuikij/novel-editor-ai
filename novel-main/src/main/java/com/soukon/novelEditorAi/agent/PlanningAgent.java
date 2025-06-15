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

 import com.fasterxml.jackson.annotation.JsonPropertyDescription;
 import com.soukon.novelEditorAi.llm.LlmService;
 import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
 import com.soukon.novelEditorAi.model.chapter.PlanContext;
 import com.soukon.novelEditorAi.model.chapter.PlanState;
 import com.soukon.novelEditorAi.model.chapter.PlanRes;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.chat.messages.Message;
 import org.springframework.ai.chat.messages.SystemMessage;
 import org.springframework.ai.chat.messages.UserMessage;
 import org.springframework.ai.chat.prompt.Prompt;
 import org.springframework.ai.chat.prompt.PromptTemplate;
 import org.springframework.ai.converter.BeanOutputConverter;

 import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;

 /**
  * 计划制定智能体
  * 专门负责在章节内容生成前的计划制定阶段，使用ReAct模式：
  * 1. Think: 分析当前情况，确定需要调用哪些工具获取信息
  * 2. Act: 调用工具获取必要信息
  * 3. 重复上述过程直到收集足够信息
  * 4. 最终输出结构化的写作计划
  */
 public class PlanningAgent extends ReActAgent {

     private static final Logger log = LoggerFactory.getLogger(PlanningAgent.class);

     @Data
     @NoArgsConstructor
     public static class PlanningThinkRes {
         @JsonPropertyDescription("当前是否已经收集到足够的信息来制定计划")
         private Boolean hasEnoughInfo;
         @JsonPropertyDescription("需要调用的工具列表，如果hasEnoughInfo为true则可以为空")
         private List<ToolCallPlan> toolsToCall;
         @JsonPropertyDescription("当前分析的思考过程")
         private String reasoning;

         @Override
         public String toString() {
             StringBuilder sb = new StringBuilder();
             sb.append("计划制定思考结果：\n");
             sb.append("推理过程：").append(reasoning).append("\n");
             sb.append("是否有足够信息：").append(hasEnoughInfo ? "是" : "否").append("\n");

             if (toolsToCall != null && !toolsToCall.isEmpty()) {
                 sb.append("需要调用的工具：\n");
                 for (int i = 0; i < toolsToCall.size(); i++) {
                     ToolCallPlan tool = toolsToCall.get(i);
                     sb.append("  ").append(i + 1).append(". ").append(tool.getToolName())
                             .append(" - ").append(tool.getPurpose()).append("\n");
                 }
             }

             return sb.toString();
         }
     }

     @Data
     @NoArgsConstructor
     public static class ToolCallPlan {
         @JsonPropertyDescription("工具名称")
         private String toolName;
         @JsonPropertyDescription("调用此工具的目的")
         private String purpose;
         @JsonPropertyDescription("工具参数，JSON格式")
         private Map<String, Object> parameters;
     }

     // 思考阶段的系统提示词
     private final String thinkSystemPrompt = """
            你是一位资深的文学编辑和创作导师，专门负责制定小说章节的写作计划。你的任务是分析当前情况，确定需要获取哪些信息来制定最佳的写作计划。

            **核心职责：**
            1. **信息需求分析：** 分析当前已有信息，识别制定写作计划所需的关键信息缺口
            2. **工具调用策略：** 确定需要调用哪些工具来获取必要信息
            3. **信息充分性判断：** 判断当前信息是否足够制定详细的写作计划

            **可用工具：**
            - latest_content_get: 获取最新的章节内容，了解当前写作进度和上下文
            - get_character_info: 获取特定角色的详细信息，包括性格、背景、关系等
            - rag_query: 检索相关的背景信息、设定资料等

            **分析原则：**
            1. **优先获取上下文：** 首先确保了解当前章节的写作状态和前文内容
            2. **角色信息完整性：** 确保涉及的关键角色信息充分
            3. **避免冗余调用：** 只调用真正需要的工具，避免获取无关信息
            4. **信息质量优先：** 宁可多调用一次工具，也要确保信息的完整性和准确性

            **输出要求：**
            - 如果信息不足，明确指出需要调用哪些工具及其目的
            - 如果信息充足，可以开始制定写作计划
            - 始终提供清晰的推理过程
            """;

     // 思考阶段的提示词模板
     private final String thinkPromptTemplate = """
            ## 当前任务：制定章节写作计划

            **章节信息：**
            - 章节ID: {chapterId}
            - 项目ID: {projectId}
            - 计划ID: {planId}

            **当前上下文：**
            {contextInfo}

            **已获取的工具信息：**
            {gatheredInfo}

            **分析要求：**
            请分析当前情况，确定是否需要调用工具获取更多信息来制定写作计划。

            考虑以下方面：
            1. 是否需要了解当前章节的最新内容和写作进度？
            2. 是否需要获取关键角色的详细信息？
            3. 是否需要检索相关的背景设定或前文信息？
            4. 当前信息是否足够制定详细的分步写作计划？

            输出格式：{format}
            """;

     // 行动阶段的系统提示词
     private final String actionSystemPrompt = """
            你是一位资深的文学编辑和创作导师，现在需要基于收集到的信息制定详细的章节写作计划。

            **核心任务：**
            根据已收集的所有信息，制定一个结构化的、可执行的章节写作计划。

            **计划制定原则：**
            1. **目标明确：** 每个步骤都有清晰的写作目标和预期字数
            2. **逻辑连贯：** 步骤之间有合理的逻辑关系和过渡
            3. **细节具体：** 提供足够的细节指导，但不过度限制创作自由度
            4. **可执行性：** 每个步骤都是可以直接执行的写作任务

            **输出要求：**
            输出结构化的JSON格式写作计划，包含总体目标和分步计划。
            """;

     // 行动阶段的提示词模板
     private final String actionPromptTemplate = """
            ## 制定写作计划

            **基础信息：**
            - 章节ID: {chapterId}
            - 项目ID: {projectId}
            - 目标字数: {targetWordCount}
            - 写作建议: {promptSuggestion}

            **上下文信息：**
            {contextInfo}

            **收集到的工具信息：**
            {gatheredInfo}

            **任务要求：**
            基于以上所有信息，制定一个详细的章节写作计划。

            计划应该包含：
            1. 总体写作目标和主题
            2. 分步骤的写作计划，每步包含：
               - 具体的写作内容描述
               - 预期字数
               - 重点关注的元素（角色、情节、氛围等）

            请直接输出JSON格式的计划，格式参考：
            {
              "goal": "总体写作目标",
              "planList": [
                {
                  "planContent": "第一步的具体写作内容",
                  "goalWordCount": 300
                },
                {
                  "planContent": "第二步的具体写作内容", 
                  "goalWordCount": 400
                }
              ]
            }
            """;

     private BeanOutputConverter<PlanningThinkRes> thinkConverter = new BeanOutputConverter<>(PlanningThinkRes.class);
     private BeanOutputConverter<PlanRes> planConverter = new BeanOutputConverter<>(PlanRes.class);

     // 存储收集到的工具信息
     private Map<String, String> gatheredInfo = new HashMap<>();
     private boolean planningCompleted = false;
     private PlanRes finalPlan = null;

     public PlanningAgent(LlmService llmService, ChapterContentRequest request) {
         super(llmService, request);
     }

     @Override
     protected boolean think() {
         try {
             if (planningCompleted) {
                 log.info("[PlanningAgent-Think] 计划制定已完成，跳过思考阶段");
                 return false;
             }

             PlanContext planContext = this.chapterContentRequest.getPlanContext();
             planContext.setPlanState(PlanState.PLANNING);
             planContext.setMessage("正在分析信息需求...");

             // 准备思考提示词
             PromptTemplate promptTemplate = new PromptTemplate(thinkPromptTemplate);
             Map<String, Object> templateData = new HashMap<>();
             templateData.put("chapterId", chapterContentRequest.getChapterId());
             templateData.put("projectId", chapterContentRequest.getChapterContext().getProjectId());
             templateData.put("planId", planContext.getPlanId());
             templateData.put("contextInfo", buildContextInfo());
             templateData.put("gatheredInfo", buildGatheredInfo());
             templateData.put("format", thinkConverter.getFormat());

             Message thinkMessage = promptTemplate.createMessage(templateData);
             List<Message> messageList = new ArrayList<>();

             // 添加系统提示词
             SystemMessage systemMessage = new SystemMessage(thinkSystemPrompt);
             messageList.add(systemMessage);
             messageList.add(thinkMessage);

             log.info("[PlanningAgent-Think] 开始思考分析...");
             String content = llmService.getAgentChatClient(planId)
                     .getChatClient()
                     .prompt(new Prompt(messageList))
                     .call()
                     .content();

             log.info("[PlanningAgent-Think] 思考结果: {}", content);
             PlanningThinkRes thinkResult = thinkConverter.convert(content);

             if (thinkResult == null) {
                 log.error("[PlanningAgent-Think] 思考结果解析失败");
                 return false;
             }

             log.info("[PlanningAgent-Think] 解析后的思考结果: {}", thinkResult);

             // 如果已有足够信息，准备进入计划制定阶段
             if (Boolean.TRUE.equals(thinkResult.getHasEnoughInfo())) {
                 log.info("[PlanningAgent-Think] 信息充足，准备制定计划");
                 return true; // 需要执行行动（制定计划）
             }

             // 如果需要调用工具，也返回true进入行动阶段
             if (thinkResult.getToolsToCall() != null && !thinkResult.getToolsToCall().isEmpty()) {
                 log.info("[PlanningAgent-Think] 需要调用工具获取信息: {}", thinkResult.getToolsToCall().size());
                 return true; // 需要执行行动（调用工具）
             }

             log.warn("[PlanningAgent-Think] 思考结果不明确，跳过行动");
             return false;

         } catch (Exception e) {
             log.error("[PlanningAgent-Think] 思考阶段执行失败: {}", e.getMessage(), e);
             return false;
         }
     }

     @Override
     protected AgentExecResult act() {
         try {
             PlanContext planContext = this.chapterContentRequest.getPlanContext();

             // 如果还没有足够信息，先调用工具
             if (!hasEnoughInfoForPlanning()) {
                 return executeToolCalls();
             }

             // 如果有足够信息，制定最终计划
             return generateFinalPlan();

         } catch (Exception e) {
             log.error("[PlanningAgent-Act] 行动阶段执行失败: {}", e.getMessage(), e);
             return new AgentExecResult("计划制定失败: " + e.getMessage(), AgentState.FAILED);
         }
     }

     /**
      * 执行工具调用获取信息
      */
     private AgentExecResult executeToolCalls() {
         log.info("[PlanningAgent-Act] 开始调用工具获取信息");

         PlanContext planContext = this.chapterContentRequest.getPlanContext();
         planContext.setMessage("正在调用工具获取信息...");

         // 构建工具调用提示词
         String toolCallPrompt = buildToolCallPrompt();

         List<Message> messageList = new ArrayList<>();
         messageList.add(new UserMessage(toolCallPrompt));

         log.info("[PlanningAgent-Act] 工具调用提示词: {}", toolCallPrompt);

         // 调用LLM执行工具调用
         String toolResult = llmService.getAgentChatClient(planId)
                 .getChatClient()
                 .prompt(new Prompt(messageList))
                 .call()
                 .content();

         log.info("[PlanningAgent-Act] 工具调用结果: {}", toolResult);

         // 存储工具调用结果
         String timestamp = String.valueOf(System.currentTimeMillis());
         gatheredInfo.put("tool_result_" + timestamp, toolResult);

         planContext.setMessage("工具调用完成，继续分析...");

         return new AgentExecResult("工具调用完成", AgentState.IN_PROGRESS);
     }

     /**
      * 生成最终的写作计划
      */
     private AgentExecResult generateFinalPlan() {
         log.info("[PlanningAgent-Act] 开始制定最终写作计划");

         PlanContext planContext = this.chapterContentRequest.getPlanContext();
         planContext.setMessage("正在制定写作计划...");

         // 准备计划制定提示词
         PromptTemplate promptTemplate = new PromptTemplate(actionPromptTemplate);
         Map<String, Object> templateData = new HashMap<>();
         templateData.put("chapterId", chapterContentRequest.getChapterId());
         templateData.put("projectId", chapterContentRequest.getChapterContext().getProjectId());
         templateData.put("targetWordCount", chapterContentRequest.getMaxTokens());
         templateData.put("promptSuggestion", chapterContentRequest.getPromptSuggestion());
         templateData.put("contextInfo", buildContextInfo());
         templateData.put("gatheredInfo", buildGatheredInfo());

         Message actionMessage = promptTemplate.createMessage(templateData);
         List<Message> messageList = new ArrayList<>();

         // 添加系统提示词
         SystemMessage systemMessage = new SystemMessage(actionSystemPrompt);
         messageList.add(systemMessage);
         messageList.add(actionMessage);

         log.info("[PlanningAgent-Act] 开始生成写作计划...");
         String planContent = llmService.getAgentChatClient(planId)
                 .getChatClient()
                 .prompt(new Prompt(messageList))
                 .call()
                 .content();

         log.info("[PlanningAgent-Act] 生成的计划内容: {}", planContent);

         try {
             // 解析计划结果
             finalPlan = planConverter.convert(planContent);
             if (finalPlan != null) {
                 planningCompleted = true;
                 planContext.setMessage("写作计划制定完成");
                 planContext.setPlanState(PlanState.COMPLETED);

                 log.info("[PlanningAgent-Act] 计划制定成功: {}", finalPlan);
                 return new AgentExecResult("计划制定完成", AgentState.COMPLETED);
             } else {
                 log.error("[PlanningAgent-Act] 计划解析失败");
                 return new AgentExecResult("计划解析失败", AgentState.FAILED);
             }
         } catch (Exception e) {
             log.error("[PlanningAgent-Act] 计划解析异常: {}", e.getMessage(), e);
             return new AgentExecResult("计划解析异常: " + e.getMessage(), AgentState.FAILED);
         }
     }

     /**
      * 构建工具调用提示词
      */
     private String buildToolCallPrompt() {
         StringBuilder prompt = new StringBuilder();
         prompt.append("请调用以下工具获取信息：\n\n");

         // 基本的工具调用
         prompt.append("1. 调用 latest_content_get 工具获取最新内容\n");
         prompt.append("   参数: chapterId=\"").append(chapterContentRequest.getChapterId()).append("\"");
         prompt.append(", wordCount=1000");
         prompt.append(", planId=\"").append(planId).append("\"\n\n");

         // 如果有角色信息需求，调用角色工具
         if (chapterContentRequest.getChapterContext().getCharacters() != null &&
                 !chapterContentRequest.getChapterContext().getCharacters().isEmpty()) {
             prompt.append("2. 调用 get_character_info 工具获取主要角色信息\n");
             prompt.append("   参数: chapterId=\"").append(chapterContentRequest.getChapterId()).append("\"");
             prompt.append(", projectId=\"").append(chapterContentRequest.getChapterContext().getProjectId()).append("\"");
             prompt.append(", name=\"").append(chapterContentRequest.getChapterContext().getCharacters().get(0).getName()).append("\"");
             prompt.append(", planId=\"").append(planId).append("\"\n\n");
         }

         return prompt.toString();
     }

     /**
      * 构建上下文信息字符串
      */
     private String buildContextInfo() {
         StringBuilder context = new StringBuilder();

         if (chapterContentRequest.getChapterContext() != null) {
             context.append("小说标题: ").append(chapterContentRequest.getChapterContext().getNovelTitle()).append("\n");
             context.append("章节摘要: ").append(chapterContentRequest.getChapterContext().getChapterSummary()).append("\n");

             if (chapterContentRequest.getChapterContext().getPreviousChapterSummary() != null) {
                 context.append("前章摘要: ").append(chapterContentRequest.getChapterContext().getPreviousChapterSummary()).append("\n");
             }
         }

         return context.toString();
     }

     /**
      * 构建已收集信息的字符串
      */
     private String buildGatheredInfo() {
         if (gatheredInfo.isEmpty()) {
             return "暂无收集到的工具信息";
         }

         StringBuilder info = new StringBuilder();
         for (Map.Entry<String, String> entry : gatheredInfo.entrySet()) {
             info.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
         }

         return info.toString();
     }

     /**
      * 判断是否有足够信息制定计划
      */
     private boolean hasEnoughInfoForPlanning() {
         // 简单的判断逻辑：如果已经调用过工具获取信息，就认为有足够信息
         return !gatheredInfo.isEmpty();
     }

     @Override
     protected Message getNextStepWithEnvMessage() {
         return new UserMessage("继续制定计划");
     }

     @Override
     public String getName() {
         return "PlanningAgent";
     }

     @Override
     public String getDescription() {
         return "专门负责章节写作计划制定的智能代理，使用ReAct模式收集信息并制定计划";
     }

     @Override
     protected Message addThinkPrompt(List<Message> messages) {
         // PlanningAgent有自己的思考逻辑，不需要额外的思考提示
         return null;
     }

     /**
      * 获取最终制定的计划
      */
     public PlanRes getFinalPlan() {
         return finalPlan;
     }

     /**
      * 重置代理状态，用于新的计划制定任务
      */
     public void reset() {
         gatheredInfo.clear();
         planningCompleted = false;
         finalPlan = null;
         setState(AgentState.IN_PROGRESS);
     }
 }