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
 import org.springframework.ai.chat.messages.AssistantMessage;
 import org.springframework.ai.chat.messages.Message;
 import org.springframework.ai.chat.messages.SystemMessage;
 import org.springframework.ai.chat.messages.UserMessage;
 import org.springframework.ai.chat.model.ChatResponse;
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

     // 最大工具调用轮次，防止无限循环
     private static final int MAX_TOOL_CALL_ROUNDS = 3;

     // 当前工具调用轮次
     private int currentToolCallRound = 0;

     @Data
     @NoArgsConstructor
     public static class PlanningThinkRes {
         @JsonPropertyDescription("当前是否已经收集到足够的信息来制定计划")
         private Boolean hasEnoughInfo;
         @JsonPropertyDescription("需要调用的工具列表，如果hasEnoughInfo为true则可以为空")
         private List<ToolCallPlan> toolsToCall;
         @JsonPropertyDescription("当前分析的思考过程")
         private String reasoning;
         @JsonPropertyDescription("调用工具返回的结果整合")
         private String callResult;

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
             sb.append("调用工具返回的结果整合：").append(callResult).append("\n");
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
             - 当前工具调用轮次: {currentRound}/{maxRounds}

             **当前上下文：**
             {contextInfo}

             ## 已经调用工具收集到的信息：
             {toolsCallResult}

             **分析要求：**
             请分析当前情况，确定是否需要调用工具获取更多信息来制定写作计划。

             考虑以下方面：
             1. 是否需要了解当前章节的最新内容和写作进度？
             2. 是否需要获取关键角色的详细信息？
             3. 是否需要检索相关的背景设定或前文信息？
             4. 当前信息是否足够制定详细的分步写作计划？
             5. 是否已达到最大工具调用轮次限制？

             输出格式：{format}
             """;

     // 工具调用阶段的系统提示词
     private final String toolCallSystemPrompt = """
             你是一位专业的小说创作助手。现在需要你调用指定的工具来获取制定写作计划所需的信息。

             **重要说明：**
             1. 你必须严格按照要求调用工具，不要跳过工具调用步骤
             2. 工具调用完成后，请简要总结获取到的信息
             3. 如果工具调用失败，请说明具体原因

             **可用工具：**
             - latest_content_get: 获取最新章节内容
             - get_character_info: 获取角色详细信息
             - rag_query: 检索相关背景信息
             """;

     // 行动阶段的提示词模板
     private final String actionPromptTemplate = """
             ## 请根据以下上下文信息，分析并创作符合要求的写作计划：

             ## 基础信息：
             - 章节ID: {chapterId}
             - 项目ID: {projectId}

             ## 需要创作的情节信息：

             ## 上下文信息：
             {contextInfo}

             ## 已经调用工具收集到的信息：
             {toolsCallResult}
                          

                          
             ## 直接输出结构化json格式，不需要额外的任何解释说明！

             """;


     private BeanOutputConverter<PlanRes> planConverter = new BeanOutputConverter<>(PlanRes.class);

     // 行动阶段的系统提示词
     String actionSystemPrompt = """
             你是一位资深的文学编辑和创作导师，拥有丰富的小说创作和指导经验。
                         
             你的任务是根据提供的小说上下文，制定一个高质量的写作计划。这个计划应该：
                         
             ## 核心原则
             1. **文学性优先**：每个步骤都要考虑文学价值，而不仅仅是情节推进
             2. **节奏控制**：合理安排叙事节奏，做到张弛有度
             3. **情感深度**：注重人物内心世界的挖掘和情感的细腻表达
             4. **细节丰富**：通过具体的细节来营造氛围和推进情节
             5. **语言美感**：追求语言的优美和表达的精准
                         
             ## 计划要求
             - 将写作任务分解为3-6个逻辑清晰的步骤
             - 每个步骤都有明确的文学目标（不仅仅是情节目标）
             - 字数分配要合理，确保每个步骤都有足够的发挥空间
             - 步骤之间要有良好的衔接和递进关系，列出的计划不能遗漏情节描述里的任何内容。
             - 最后一个步骤要为后续章节留下合适的悬念或转折
                         
             ## 避免的问题
             - 避免机械化的情节推进
             - 避免过于直白的叙述
             - 避免忽视人物的内心活动
             - 避免缺乏环境和氛围的营造
             - 避免语言平淡无味
                      
             直接输出结构化json格式，不需要额外的任何解释说明！
                         
             输出的json格式为：{%s}
             """.formatted(planConverter.getFormat());

     private BeanOutputConverter<PlanningThinkRes> thinkConverter = new BeanOutputConverter<>(PlanningThinkRes.class);

     // 存储收集到的工具信息
     private Map<String, String> gatheredInfo = new HashMap<>();
     private String toolsCallResult = "";
     private boolean planningCompleted = false;
     private PlanRes finalPlan = null;

     // 存储最近一次的思考结果
     private PlanningThinkRes lastThinkResult = null;

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

             // 检查是否超过最大工具调用轮次
             if (currentToolCallRound >= MAX_TOOL_CALL_ROUNDS) {
                 log.info("[PlanningAgent-Think] 已达到最大工具调用轮次，强制进入计划制定阶段");
                 return true; // 强制进入计划制定
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
             templateData.put("currentRound", currentToolCallRound);
             templateData.put("maxRounds", MAX_TOOL_CALL_ROUNDS);
             templateData.put("contextInfo", chapterContentRequest.getChapterContextStr());
             templateData.put("toolsCallResult", this.toolsCallResult);
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
             this.toolsCallResult = this.toolsCallResult + "\n"
                                    + "第" + currentToolCallRound + "次工具调用结果：\n" + thinkResult.getCallResult();
             // 保存思考结果
             lastThinkResult = thinkResult;
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

             // 如果还没有足够信息且未超过最大轮次，先调用工具
//             if (!hasEnoughInfoForPlanning() && currentToolCallRound < MAX_TOOL_CALL_ROUNDS) {
//                 return executeToolCalls();
//             }

             // 如果有足够信息或已达到最大轮次，制定最终计划
             log.info("[PlanningAgent-Act] 开始制定最终写作计划");
             planContext.setMessage("正在制定写作计划...");

             // 准备计划制定提示词
             PromptTemplate promptTemplate = new PromptTemplate(actionPromptTemplate);
             Map<String, Object> templateData = new HashMap<>();
             templateData.put("chapterId", chapterContentRequest.getChapterId());
             templateData.put("projectId", chapterContentRequest.getChapterContext().getProjectId());
             templateData.put("contextInfo", this.chapterContentRequest.getChapterContextStr());
             templateData.put("toolsCallResult", this.toolsCallResult);

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


         } catch (Exception e) {
             log.error("[PlanningAgent-Act] 行动阶段执行失败: {}", e.getMessage(), e);
             return new AgentExecResult("计划制定失败: " + e.getMessage(), AgentState.FAILED);
         }
     }

     /**
      * 执行工具调用获取信息
      */
     private AgentExecResult executeToolCalls() {
         log.info("[PlanningAgent-Act] 开始调用工具获取信息，当前轮次: {}", currentToolCallRound + 1);

         PlanContext planContext = this.chapterContentRequest.getPlanContext();
         planContext.setMessage("正在调用工具获取信息...");

         try {
             // 构建工具调用提示词
             String toolCallPrompt = buildToolCallPrompt();

             List<Message> messageList = new ArrayList<>();
             messageList.add(new SystemMessage(toolCallSystemPrompt));
             messageList.add(new UserMessage(toolCallPrompt));

             log.info("[PlanningAgent-Act] 工具调用提示词: {}", toolCallPrompt);

             // 调用LLM执行工具调用（Spring AI会自动处理工具调用）
             ChatResponse response = llmService.getAgentChatClient(planId)
                     .getChatClient()
                     .prompt(new Prompt(messageList))
                     .call()
                     .chatResponse();

             // 处理工具调用结果
             String toolResult = response.getResult().getOutput().getText();
             List<AssistantMessage.ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();

             log.info("[PlanningAgent-Act] 工具调用完成，调用数量: {}", toolCalls != null ? toolCalls.size() : 0);
             log.info("[PlanningAgent-Act] 工具调用结果: {}", toolResult);

             // 存储工具调用结果
             String timestamp = String.valueOf(System.currentTimeMillis());
             gatheredInfo.put("tool_result_round_" + (currentToolCallRound + 1) + "_" + timestamp, toolResult);

             // 如果有工具调用，存储详细信息
             if (toolCalls != null && !toolCalls.isEmpty()) {
                 for (int i = 0; i < toolCalls.size(); i++) {
                     AssistantMessage.ToolCall toolCall = toolCalls.get(i);
                     gatheredInfo.put("tool_call_" + (currentToolCallRound + 1) + "_" + i + "_" + timestamp,
                             "工具: " + toolCall.name() + ", 参数: " + toolCall.arguments());
                 }
             }

             // 增加工具调用轮次
             currentToolCallRound++;

             planContext.setMessage("工具调用完成，继续分析...");

             return new AgentExecResult("工具调用完成，轮次: " + currentToolCallRound, AgentState.IN_PROGRESS);

         } catch (Exception e) {
             log.error("[PlanningAgent-Act] 工具调用失败: {}", e.getMessage(), e);

             // 即使工具调用失败，也增加轮次计数，避免无限重试
             currentToolCallRound++;

             // 存储错误信息
             String timestamp = String.valueOf(System.currentTimeMillis());
             gatheredInfo.put("tool_error_round_" + currentToolCallRound + "_" + timestamp,
                     "工具调用失败: " + e.getMessage());

             return new AgentExecResult("工具调用失败，继续制定计划: " + e.getMessage(), AgentState.IN_PROGRESS);
         }
     }

     /**
      * 构建工具调用提示词
      */
     private String buildToolCallPrompt() {
         StringBuilder prompt = new StringBuilder();
         prompt.append("请根据当前情况调用必要的工具获取信息：\n\n");

         // 如果是第一轮，优先获取最新内容
         if (currentToolCallRound == 0) {
             prompt.append("**第一优先级：获取最新内容**\n");
             prompt.append("调用 latest_content_get 工具获取最新章节内容\n");
             prompt.append("参数: chapterId=\"").append(chapterContentRequest.getChapterId()).append("\"");
             prompt.append(", wordCount=1000");
             prompt.append(", planId=\"").append(planId).append("\"\n\n");
         }

         // 如果有角色信息且还未获取，调用角色工具
         if (chapterContentRequest.getChapterContext().getCharacters() != null &&
             !chapterContentRequest.getChapterContext().getCharacters().isEmpty() &&
             !hasCharacterInfo()) {
             prompt.append("**第二优先级：获取角色信息**\n");
             prompt.append("调用 get_character_info 工具获取主要角色信息\n");
             prompt.append("参数: chapterId=\"").append(chapterContentRequest.getChapterId()).append("\"");
             prompt.append(", projectId=\"").append(chapterContentRequest.getChapterContext().getProjectId()).append("\"");
             prompt.append(", name=\"").append(chapterContentRequest.getChapterContext().getCharacters().get(0).getName()).append("\"");
             prompt.append(", planId=\"").append(planId).append("\"\n\n");
         }

         // 如果需要更多背景信息，调用RAG工具
         if (currentToolCallRound >= 1 && !hasRagInfo()) {
             prompt.append("**第三优先级：检索相关信息**\n");
             prompt.append("调用 rag_query 工具检索相关背景信息\n");
             prompt.append("参数: chapterId=\"").append(chapterContentRequest.getChapterId()).append("\"");
             prompt.append(", query=\"背景信息\"");
             prompt.append(", planId=\"").append(planId).append("\"\n\n");
         }

         prompt.append("**重要提示：**\n");
         prompt.append("1. 请严格按照上述参数调用工具\n");
         prompt.append("2. 工具调用完成后，请简要总结获取到的信息\n");
         prompt.append("3. 如果某个工具调用失败，请说明原因并继续其他工具调用\n");

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

             if (chapterContentRequest.getChapterContext().getCharacters() != null &&
                 !chapterContentRequest.getChapterContext().getCharacters().isEmpty()) {
                 context.append("主要角色: ");
                 chapterContentRequest.getChapterContext().getCharacters().forEach(character ->
                         context.append(character.getName()).append(" "));
                 context.append("\n");
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
         info.append("已收集的信息：\n");
         for (Map.Entry<String, String> entry : gatheredInfo.entrySet()) {
             info.append("- ").append(entry.getKey()).append(": ");
             String value = entry.getValue();
             // 限制显示长度，避免提示词过长
             if (value.length() > 200) {
                 value = value.substring(0, 200) + "...";
             }
             info.append(value).append("\n");
         }

         return info.toString();
     }

     /**
      * 判断是否有足够信息制定计划
      */
     private boolean hasEnoughInfoForPlanning() {
         // 改进的判断逻辑
         boolean hasBasicInfo = !gatheredInfo.isEmpty();
         boolean hasContentInfo = gatheredInfo.keySet().stream()
                 .anyMatch(key -> key.contains("latest_content") || key.contains("tool_result"));
         boolean reachedMaxRounds = currentToolCallRound >= MAX_TOOL_CALL_ROUNDS;

         // 如果已达到最大轮次，强制认为信息充足
         if (reachedMaxRounds) {
             log.info("[PlanningAgent] 已达到最大工具调用轮次，强制认为信息充足");
             return true;
         }

         // 如果有基本信息且有内容信息，认为可以制定计划
         return hasBasicInfo && hasContentInfo;
     }

     /**
      * 检查是否已获取角色信息
      */
     private boolean hasCharacterInfo() {
         return gatheredInfo.keySet().stream()
                 .anyMatch(key -> key.contains("character") || key.contains("get_character_info"));
     }

     /**
      * 检查是否已获取RAG信息
      */
     private boolean hasRagInfo() {
         return gatheredInfo.keySet().stream()
                 .anyMatch(key -> key.contains("rag") || key.contains("rag_query"));
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
      * 获取收集到的信息摘要
      */
     public Map<String, String> getGatheredInfo() {
         return new HashMap<>(gatheredInfo);
     }

     /**
      * 获取当前工具调用轮次
      */
     public int getCurrentToolCallRound() {
         return currentToolCallRound;
     }

     /**
      * 获取最后一次思考结果
      */
     public PlanningThinkRes getLastThinkResult() {
         return lastThinkResult;
     }

     /**
      * 重置代理状态，用于新的计划制定任务
      */
     public void reset() {
         gatheredInfo.clear();
         planningCompleted = false;
         finalPlan = null;
         lastThinkResult = null;
         currentToolCallRound = 0;
         setState(AgentState.IN_PROGRESS);
         log.info("[PlanningAgent] 代理状态已重置");
     }
 }