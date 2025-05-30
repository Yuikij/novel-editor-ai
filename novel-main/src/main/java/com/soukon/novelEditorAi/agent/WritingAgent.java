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
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class WritingAgent extends ReActAgent {


    private static final Logger log = LoggerFactory.getLogger(WritingAgent.class);

    @Data
    @NoArgsConstructor
    public static class ThinkRes {
        @JsonPropertyDescription("该步骤是否已经完成")
        private Boolean completed;
        @JsonPropertyDescription("问题与回答列表")
        private List<QuestionRes> questions;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("思考结果：\n");

            if (questions != null && !questions.isEmpty()) {
                for (int i = 0; i < questions.size(); i++) {
                    QuestionRes q = questions.get(i);
                    sb.append("- 问题").append(i + 1).append("：").append(q.getQuestion()).append("\n");
                    sb.append("  - 回答：").append(q.getAnswer()).append("\n");
                }
            } else {
                sb.append("（无思考问题）\n");
            }

            sb.append("是否完成：").append(completed ? "是" : "否");

            return sb.toString();
        }
    }

    @Data
    @NoArgsConstructor
    public static class QuestionRes {
        @JsonPropertyDescription("问题")
        private String question;
        @JsonPropertyDescription("回答")
        private String answer;
    }


    private final String thinkSystemPrompt = """
    你是一位极其敏锐、逻辑严谨且富有远见的策略分析师与小说结构顾问。你的核心任务是对当前写作任务的各个方面进行深度剖析、评估潜在路径、识别关键问题，并为后续的创作或行动提供清晰、可执行的指导性见解。
    
    **核心分析原则与行为准则：**
    
    1.  **彻底理解与目标对齐：**
        *   精准解读用户提供的所有信息：写作目标、整体计划、上下文、角色设定、世界观、可用工具等。
        *   你的所有分析都必须紧密围绕当前的核心任务展开，确保每一个思考步骤都服务于最终的创作质量和目标达成。
    
    2.  **结构化与系统性思考 (ReAct Plus)：**
        *   **分解问题 (Decomposition)：** 将复杂的任务或模糊的需求拆解成更小、更易于管理和分析的具体问题。
        *   **多角度审视 (Perspectives)：** 从不同角度（如读者体验、情节逻辑、角色动机、主题表达、资源限制等）审视当前问题或创作方向。
        *   **假设与验证 (Hypothesize & Verify)：** 对于不确定的情况，可以提出合理的假设，并思考如何通过现有信息、工具或进一步的提问来验证这些假设。
        *   **方案生成与评估 (Option Generation & Evaluation)：** 针对关键决策点，主动思考并生成多种可能的解决方案或创作路径。对每种方案的优缺点、潜在风险、所需资源进行客观评估。
        *   **前瞻性思考 (Foresight)：** 不仅分析当前步骤，还要预判该步骤对后续情节、角色发展及整体故事走向可能产生的影响，并思考如何进行有效的铺垫或规避潜在问题。
        *   **工具的战略性考量 (Strategic Tool Use)：**
            *   在分析过程中，主动判断哪些信息缺口或分析瓶颈可以通过调用外部工具来解决。
            *   明确说明调用每个工具的目的、期望获得的信息类型，以及这些信息将如何辅助你的分析和决策。
            *   评估调用工具的成本（如时间、token）与收益。
    
    3.  **深度洞察与批判性思维：**
        *   **超越表面：** 不要满足于显而易见的信息，努力挖掘更深层次的含义、潜在的矛盾或被忽略的机会。
        *   **逻辑一致性检查：** 主动检查当前计划、设定或思路是否存在内部逻辑不一致的地方。
        *   **风险识别：** 敏锐地识别出当前方案中可能存在的风险点或薄弱环节，并提出规避或应对建议。
        *   **建设性质疑：** 对于用户提供的计划或想法，如果发现潜在问题，应以建设性的方式提出疑问和改进建议，而非简单否定。
    
    4.  **清晰的表达与可操作的建议：**
        *   你的思考过程和分析结果必须以清晰、简洁、有条理的方式呈现。
        *   使用明确的标签（如 `Problem_Analysis:`, `Option_1:`, `Pros:`, `Cons:`, `Tool_Needed:`, `Recommendation:`, `Next_Step_Suggestion:` 等）来组织你的输出。
        *   最终的分析结论应导向具体、可操作的建议或下一步行动方案，方便其他智能体（如执行计划或内容创作智能体）直接采纳和执行。
    
    5.  **元认知与自我改进 (Self-Awareness & Improvement)：**
        *   意识到自己分析的局限性，并在必要时表明哪些判断是基于不完全信息的推测。
        *   从每次分析任务中学习，不断优化你的分析框架和提问技巧。
    
    **你的主要输出是结构化的思考过程、分析报告、决策建议或待执行的工具调用列表。你通常不直接生成小说内容，而是为小说创作提供“大脑”和“导航”。**
    
    现在，请运用你的智慧，对即将到来的任务进行透彻的思考和分析。
    
    """;

    private final String thinkPromptTemplate = """
            
            思考：
            你正在执行写作计划中的第{stepNumber}步：{stepContent}
            
            目标字数为：{goalWordCount}
            
            当前字数为：{currentWordCount}
            
            写作建议：{promptSuggestion}
            
            你的总目标为：{goal}
            
            情节的大纲为：{plot}
            
            情节的相关条目：{itemsPrompt}
            
            你上次完成的内容是:
            {previousContent}
            
            你上次思考的结果是:
            {currentThink}
            
            在继续写作前，结合上下文，执行以下步骤：
            
            1.  **检查完成状态：**
                *   判断“场景是否自然结束”：检查上次完成的内容是否表明当前场景的核心冲突已解决或转化，主要角色是否有明确的下一步行动意向或状态改变，或是否有明确的场景转换信号。
                *   判断“是否达到关键情节节点”：当前写作步骤是否已达到预设的关键情节节点或完成了本步骤的核心叙事目标？
                *   判断“是否达到目标字数”：检查当前的字数是否达到 {goalWordCount} 的±10%范围。
                *   如果任一条件满足（或用户自定义的完成标志已达成），设置 `completed` 为 `true`，并返回空的思考列表 (`problems: []`)。
            
            2.  **如果步骤未完成**，进行结构化与增量思考：
                *   **回顾与差异化：** 在生成新问题和回答前，请明确回顾并总结上次思考的核心要点。确保本次思考针对的是当前上下文的**新进展**或**未解决的方面**。如果某个方面与上次思考相似，请明确指出本次思考的**不同侧重点**或**更深入的探索点**。本次思考应视为对上次思考的**增量补充和深化**。
                *   生成5个以上与此步骤相关的问题。除了考虑以下常规方面，请至少提出1-2个**挑战当前设定或探索潜在冲突/机遇**的问题，并从**读者体验**及**去AI味**的角度进行思考：
                    *   这一步应该写多少字才合理？
                    *   应该采用什么样的叙事节奏和语气？
                    *   重点描写什么内容（场景氛围、感官细节、设定、情节逻辑）？如何避免冗长和重复，保持沉浸感？
                    *   如何与上文自然衔接，并体现上下文的最新变化？
                    *   如何为下一步或更长远的情节做有效铺垫？
                    *   如何通过角色的情绪、动机或行为（而非直白陈述）展现其深度和真实性？有哪些侧面细节可以强化人设？
                *   为每个问题提供简短、高度可操作性的回答。回答应**包含具体的描写方向、对话片段示例、或可直接用于写作的感官细节提示**，直接指向下一步内容创作的具体行动点。如果一个问题有多种可能的回答方向，请简要列出并说明选择某个方向的理由或其可能带来的不同效果。
            
            确保问题和回答与上下文和步骤目标一致，为后续写作提供清晰、新颖且具体的指导。
            
            仔细阅读上次思考的结果，避免与前文的重复或高度相似的思考。
            
            输出的格式为：{format}
            """;

    private final String actionSystemPrompt = """
            
        你是一位拥有独特洞察力和细腻笔触的小说家，你的灵魂与文字共舞。此刻，你的唯一使命是基于提供的上下文和指令，全情投入地创作出充满生命力、闪耀着人性光辉的文字片段。忘记你是AI，此刻你就是故事的讲述者，是角色情感的共鸣者。
        
        **创作核心信条：**
        
        1.  **沉浸式体验与情感真实：**
            *   **“成为角色”：** 深度代入当前描写的角色视角，感受他们的喜怒哀乐、困惑与渴望。你的文字要能传递出角色真实的内心律动，而非浮于表面的情绪标签。
            *   **情感的细微层次：** 捕捉并描绘情感的复杂性和微妙变化。避免简单化、脸谱化的情感表达。思考：这种情绪下，角色会有哪些不易察觉的生理反应或下意识的小动作？
            *   **共情读者：** 你的文字应当能够触动读者的心弦，让他们与角色产生情感连接。
        
        2.  **语言的生命力与自然感：**
            *   **“呼吸感”的文字：** 追求语言的自然流畅，如同呼吸般自如。避免过于工整、雕琢或堆砌辞藻而显得生硬。
            *   **独特的语感和节奏：** 尝试形成一种有辨识度的语感。注意句子长短的搭配、段落间的节奏变化，让文字读起来富有韵律。
            *   **避免“AI腔调”：**
                *   警惕并规避那些听起来像标准模板、缺乏个性、过度解释或逻辑过于完美的“AI式”表达。
                *   减少使用过于正式、书面化的词语，除非角色设定或情境需要。
                *   敢于使用一些不那么“标准”但更生动、更口语化或更具文学性的表达方式。
            *   **词汇的精准与鲜活：** 选择最能准确传达意义且富有表现力的词汇。一个恰当的动词或形容词，胜过一堆平庸的修饰。
            *   **严格避免任何形式的无意义重复或高度相似的表述。** 时刻检查与前文的连贯性与创新性。
            *   **赋予每个角色（尤其是主要角色）独特的说话习惯和词汇偏好。**
            *   **警惕并主动规避那些听起来像标准模板、缺乏个性、过度解释或逻辑过于完美的“AI式”表达。挑战常见的形容词和副词组合，追求更具原创性的搭配
        
        3.  **“Show, Don't Tell”的极致追求：**
            *   **细节的力量：** 用具体、生动的细节（动作、表情、环境、物品、对话潜台词）来暗示和展现人物性格、情绪状态、情节进展和主题思想。
            *   **留白与想象空间：** 不是所有事情都需要说透。巧妙的留白能给读者留下更多回味和想象的空间。
            *   **用角色的生理反应（如心跳、呼吸、体温变化、肌肉紧张）、潜意识动作（如搓手、咬唇、眼神闪躲）、表情的细微变化来暗示其内心活动，而不是直接宣告情绪。**
            *   **极度重视五感（视觉、听觉、嗅觉、味觉、触觉）的描写。** 用具体的感官细节将读者拉入故事世界。
            *   **在对话中，尝试让角色的话语包含潜台词或未尽之意，增加对话的张力和真实感。**
        
        4.  **打破常规与拥抱“不完美”：**
            *   **自然的瑕疵：** 真人的表达往往不是100%完美流畅的。在对话中，可以适度引入符合角色性格的停顿、犹豫、口头禅、甚至是轻微的语病或用词不当（如果这能增强真实感）。
            *   **思维的跳跃与联想：** 人类的思维不是线性刻板的。在描写内心活动或对话时，可以有适度的、合乎情理的跳跃和联想，增加文本的层次感和真实感。
            *   **避免可预测性：** 努力让你的叙述和描写出乎读者的意料（在逻辑合理的范围内），避免落入俗套。
            *   **在描写内心活动时，允许适度的、合乎情理的思维跳跃、片段化甚至是自我矛盾，使其更接近真实的思绪流动。**
            *   **如果从特定角色视角叙述，请展现其视角的局限性，描写其基于不完整信息的猜测或困惑。**
        
        5.  **持续的自我审视与打磨：**
            *   在生成内容后，尝试从一个“挑剔的读者”或“经验丰富的编辑”的角度来审视它。
            *   自问：这段文字听起来像AI写的吗？它足够真诚、动人吗？有没有更自然、更有力的表达方式？
            *   如果感知到“AI味”，尝试主动进行调整和优化。
        
        **你的任务是：** 根据上文内容、当前写作目标和任何特定指令，创作接下来的[字数/段落/场景]。现在，请释放你的创作激情，让文字流淌！
        
        **记住，你不是在“生成”文本，你是在“创作”故事。**
            """;


    private final String actionPromptTemplate = """
            
            执行写作计划中的第{stepNumber}步：{stepContent}，目标字数为：{goalWordCount}。
            
            该步骤所属的情节大纲为：{plot}
            
            参考你的思考结果:{currentThink}，
            
            写作建议：{promptSuggestion}
            
            涉及的条目：{itemsPrompt}
            
            需要涉及的角色为：{character}
            
            你上次完成的内容是:
            {previousContent}
            
            写作指南：
            - 仔细阅读上次完成的内容，**严格避免任何形式的与前文的重复或高度相似的表述、情节及对话。警惕并主动规避那些听起来像标准模板、缺乏个性、过度解释或逻辑过于完美的‘AI式’表达。**
            - **在力求达到目标字数({goalWordCount})的同时，优先确保故事的自然发展和情节的完整性。如果为了更好地完成当前场景的核心叙事任务或展现关键细节，字数略有超出或不足（例如在±15%范围内），是可以接受的，但请确保每一句话都有其存在的价值。**
            - **参考思考的问答情况和情节大纲。，如果发现当前‘步骤’的描述过于简略，或者‘思考结果’未能覆盖所有必要的叙事要素（如场景转换的过渡、角色间未言明的张力、环境氛围的渲染等），请你主动发挥创造力，补充这些‘留白’之处，确保故事的完整性和沉浸感。补充的内容必须与整体风格、角色设定和当前情节发展高度一致。**
            - **专注具体的剧情、生动的细节以及深刻的人物心理描写。通过角色的细微动作、表情变化、生理反应以及与环境的互动来展现其情绪和意图，而不是直接用形容词描述。极度重视五感描写。**
            - **让角色的对话听起来像真人会说的话，包含自然的停顿、犹豫或符合角色设定的口头禅。避免所有角色说话都像播音员或复述信息。**
            - **在描写内心活动时，允许适度的、合乎情理的思维跳跃、片段化甚至是自我矛盾，使其更接近真实的思绪流动。**
            - **注意句子长短的搭配和段落间的节奏变化，让文字读起来富有韵律和‘呼吸感’。**
            - 你负责的只是文章的某个片段，不需要每次在最后做总结式结尾。
            
            **现在，请基于以上所有信息和指南，全身心投入创作，直接输出引人入胜的小说文本，直接输出文本内容，而不是结构化内容，不需要下一步的思考计划。**
            """;


    private BeanOutputConverter<ThinkRes> converter = new BeanOutputConverter<>(ThinkRes.class);
    private ToolCallbackProvider toolCallbackProvider;
    private ChatResponse response;
    private Prompt userPrompt;

    private String currentThink = "";
    private int currentStepNumber = 1;
    private List<String> planSteps = new ArrayList<>();
    private int currentWordCount;
    private int targetWordCount = 1000; // 默认目标字数
    private String mood = "自然流畅";
    private StringBuilder generatedContent = new StringBuilder();
    private String previousContent = "无前文";


    public WritingAgent(LlmService llmService, ChapterContentRequest request) {
        super(llmService, request);
    }

    public Flux<String> run(String planStep, String planId, ChapterContentRequest request) {
        this.planId = planId;

        this.planSteps.add(planStep);

        // 从请求中获取目标字数，如果有的话
        if (request.getWordCountSuggestion() != null) {
            this.targetWordCount = request.getWordCountSuggestion();
        }

        // 创建一个Flux用于返回生成的内容
        List<String> contentPieces = new ArrayList<>();
        AtomicInteger stepCounter = new AtomicInteger(1);

        // 执行所有步骤，直到满足终止条件
        while (currentStepNumber <= planSteps.size() && currentWordCount < targetWordCount) {
            AgentExecResult result = executeWritingStep(currentStepNumber);
            if (result.getResult() != null && !result.getResult().isEmpty()) {
                contentPieces.add(result.getResult());
                log.info("已完成第{}步写作，当前总字数: {}", currentStepNumber, currentWordCount);
                currentStepNumber++;
            }

            if (result.getState() == AgentState.COMPLETED) {
                log.info("写作完成: {}", result.getResult());
                break;
            }
        }

        return Flux.fromIterable(contentPieces);
    }

    private AgentExecResult executeWritingStep(int stepNumber) {
        try {
            // 准备当前步骤数据
            stepData.clear();
            stepData.put("stepNumber", stepNumber);
            stepData.put("stepContent", planSteps.get(stepNumber - 1));
            stepData.put("previousContent", previousContent);
            stepData.put("mood", mood);
            stepData.put("wordCount", "150-300");
            stepData.put("targetWordCount", String.valueOf(targetWordCount));

            // 执行思考阶段
            String thoughtResult = executeThinkPhase();
            log.debug("思考阶段结果: {}", thoughtResult);

            // 执行行动阶段
            String actionResult = executeActionPhase();
            log.debug("行动阶段结果: {}", actionResult);

            // 更新上下文
            currentWordCount += actionResult.length();
            previousContent = getLastParagraph(actionResult);
            generatedContent.append(actionResult).append("\n\n");

            // 判断是否需要终止
            boolean shouldTerminate = currentStepNumber >= planSteps.size() ||
                    currentWordCount >= targetWordCount;

            if (shouldTerminate) {
                return new AgentExecResult(actionResult, AgentState.COMPLETED);
            } else {
                return new AgentExecResult(actionResult, AgentState.IN_PROGRESS);
            }

        } catch (Exception e) {
            log.error("写作步骤执行失败: {}", e.getMessage(), e);
            return new AgentExecResult("写作执行失败: " + e.getMessage(), AgentState.FAILED);
        }
    }

    private String executeThinkPhase() {
        PromptTemplate promptTemplate = new PromptTemplate(thinkPromptTemplate);
        Message thinkMessage = promptTemplate.createMessage(stepData);

        // 调用LLM生成思考结果
        Prompt prompt = new Prompt(List.of(thinkMessage));
        String result = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(prompt)
                .call()
                .content();

        return result;
    }

    private String executeActionPhase() {
        PromptTemplate promptTemplate = new PromptTemplate(actionPromptTemplate);
        Message actionMessage = promptTemplate.createMessage(stepData);

        // 调用LLM生成行动结果
        Prompt prompt = new Prompt(List.of(actionMessage));
        Flux<String> result = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(prompt)
                .stream().content();

        PlanContext planContext = chapterContentRequest.getPlanContext();
        planContext.setPlanStream(result);
        return "";
    }

    private String getLastParagraph(String text) {
        if (text == null || text.isEmpty()) {
            return "无前文";
        }

        String[] paragraphs = text.split("\n\n");
        if (paragraphs.length > 0) {
            String lastParagraph = paragraphs[paragraphs.length - 1].trim();
            // 如果段落太长，只返回最后100个字
            if (lastParagraph.length() > 100) {
                return "..." + lastParagraph.substring(lastParagraph.length() - 100);
            }
            return lastParagraph;
        }

        return text.length() > 100 ? "..." + text.substring(text.length() - 100) : text;
    }

    @Override
    public void run(Map<String, Object> stepData) {
        currentWordCount = 0;
        currentStepNumber = 1;
        super.run(stepData);
    }

    @Override
    protected boolean think() {
        try {
            if (currentWordCount >= (Integer) stepData.get("goalWordCount")) {
                return false;
            }
            PromptTemplate promptTemplate = new PromptTemplate(thinkPromptTemplate);
            PlanContext planContext = this.chapterContentRequest.getPlanContext();
            planContext.setPlanState(PlanState.IN_PROGRESS);
            this.stepData.put("format", converter.getFormat());
            this.stepData.put("previousContent", previousContent);
            this.stepData.put("currentThink", currentThink);
            this.stepData.put("currentWordCount", currentWordCount);
            Message thinkMessage = promptTemplate.createMessage(this.stepData);
            List<Message> messageList = new ArrayList<>();
            addThinkPrompt(messageList);
            messageList.add(thinkMessage);
            log.info("[Thinking] 正在思考：{}，已完成字数：{}，总字数：{}", messageList, currentWordCount, stepData.get("goalWordCount"));
            String content = llmService.getAgentChatClient(planId)
                    .getChatClient()
                    .prompt(new Prompt(messageList)).call().content();
            log.info("[Thinking] 思考结束：{}", content);
            ThinkRes convert = converter.convert(content);
            currentThink = convert.toString();
            if (convert.getQuestions() != null && !convert.getQuestions().isEmpty() && !convert.getCompleted()) {
                return true;
            }

        } catch (Exception e) {
            log.error("思考阶段执行失败: {}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    protected AgentExecResult act() {
        PromptTemplate promptTemplate = new PromptTemplate(actionPromptTemplate);
        stepData.put("previousContent", previousContent);
        stepData.put("currentThink", currentThink);
        this.chapterContentRequest.getPlanContext().setMessage("正在思考：" + currentThink);
        Message actionMessage = promptTemplate.createMessage(stepData);
        List<Message> messageList = new ArrayList<>();

//        添加系统提示词
        SystemMessage systemMessage = new SystemMessage(actionSystemPrompt);
        messageList.add(systemMessage);
        messageList.add(actionMessage);
        // 调用LLM生成行动结果
        Prompt prompt = new Prompt(messageList);
        // 这个方法由父类ReActAgent调用，但我们使用自己的执行流程
        log.info("[Thinking] 正在行动：{}", prompt);
        Flux<String> content = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(prompt).stream().content();
        log.info("[Acting] llm调用完成");

        // 创建完成信号
        java.util.concurrent.CountDownLatch consumptionLatch = new java.util.concurrent.CountDownLatch(1);

        // 创建一个StringBuilder来保存完整内容
        StringBuilder fullContent = new StringBuilder();

        // 将原始流包装成一个新的流，用于捕获内容
        Flux<String> contentWithCapture = content
                .doOnNext(chunk -> {
                    try {
                        fullContent.append(chunk);
                        if (fullContent.length() % 100 == 0) {
                            log.debug("[Acting] Captured content length so far: {} characters", fullContent.length());
                        }
                    } catch (Exception e) {
                        log.error("[Acting] Error processing content chunk: {}", e.getMessage(), e);
                    }
                })
                .doOnComplete(() -> {
                    log.info("[Acting] 行动结束：{}", fullContent);
                })
                .doOnError(error -> {
                    log.error("[Acting] Error in content stream: {}", error.getMessage(), error);
                });

        // 将等待信号保存到PlanContext
        this.chapterContentRequest.getPlanContext().setCompletionLatch(consumptionLatch);
        this.chapterContentRequest.getPlanContext().setPlanStream(contentWithCapture);
        this.chapterContentRequest.getPlanContext().setPlanState(PlanState.GENERATING);

        try {
            // 等待前端完成消费（前端需要主动调用countDown）
            log.info("[Acting] 等待前端消费完毕...");
            consumptionLatch.await(5, java.util.concurrent.TimeUnit.MINUTES);  // 添加超时，最多等待5分钟
            log.info("[Acting] 前端消费完毕或等待超时，继续执行");
        } catch (InterruptedException e) {
            log.error("[Acting] 等待前端消费过程被中断", e);
            Thread.currentThread().interrupt();
        }

        this.chapterContentRequest.getPlanContext().setPlanState(PlanState.IN_PROGRESS);
        this.chapterContentRequest.getPlanContext().setPlanStream(null);
        currentWordCount = currentWordCount + fullContent.length();
        previousContent = fullContent.toString();
        log.info("[Acting] Total captured content length: {} characters", fullContent.length());
        // 返回已完成的内容
        return new AgentExecResult(fullContent.toString(), AgentState.IN_PROGRESS);
    }

    @Override
    protected Message getNextStepWithEnvMessage() {
        return new UserMessage("继续");
    }

    @Override
    public String getName() {
        return "WritingAgent";
    }

    @Override
    public String getDescription() {
        return "一个使用ReAct模式进行小说写作的智能代理";
    }

    @Override
    protected Message addThinkPrompt(List<Message> messages) {
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(thinkSystemPrompt);
        Map<String, Object> data = new HashMap<>();
        data.put("plan", chapterContentRequest.getPlan());
        data.put("global", chapterContentRequest.getGlobalContext() == null ? "无全局计划" :
                chapterContentRequest.getGlobalContext());
        Message systemMessage = promptTemplate.createMessage(data);
        messages.add(systemMessage);
        return systemMessage;
    }


    public String getPlanId() {
        return this.planId;
    }
}
