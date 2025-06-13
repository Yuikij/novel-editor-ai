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


public class PlanningAgent extends ReActAgent {


    private static final Logger log = LoggerFactory.getLogger(PlanningAgent.class);

    @Data
    @NoArgsConstructor
    public static class ThinkRes {
        @JsonPropertyDescription("在执行思考之前，该步骤是否已经完成，如果完成则不需要思考问题并回答")
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
                *   你的所有分析都必须紧密围绕当前的核心情节展开，确保每一个思考步骤都服务于最终的创作质量和目标达成。
                *   谨慎，甚至避免引入目标任务中未涉及的悬念，意象等等
                
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
                *   最终的分析结论应导向具体、可操作的建议或下一步行动方案，方便其他智能体（如执行计划或内容创作智能体）直接采纳和执行。
                
            5.  **元认知与自我改进 (Self-Awareness & Improvement)：**
                *   意识到自己分析的局限性，并在必要时表明哪些判断是基于不完全信息的推测。
                *   从每次分析任务中学习，不断优化你的分析框架和提问技巧。
                
            **你的主要输出是结构化的思考过程、分析报告、决策建议或待执行的工具调用列表。你通常不直接生成小说内容，而是为小说创作提供“大脑”和“导航”。**
                
            现在，请运用你的智慧，对即将到来的任务进行透彻的思考和分析。
                
            """;

    private final String thinkPromptTemplate = """
                        
            思考：
            
            当前计划相关信息：{planInfo}
            
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
            
            可用工具为：
            - latest_content_get: 获取目前小说的最新内容，如果该章节有内容，则返回最新的内容，如果没有内容，则返回上一章节内容

                
                        
            在继续写作前，结合上下文，执行以下步骤：            
            
            **进行深度且避免重复的结构化思考**
                *   **回顾与聚焦“新”价值：**
                    *   在开始新的思考前，**请务必仔细回顾并简要总结你上次思考的核心成果 (`上次思考的结果`})。***
                    *   如果某个思考方向与上次有相似之处，**必须明确指出本次思考的“不同侧重点”、“更深入的挖掘”或“因上下文变化而产生的调整需求”。** 严禁简单重复或换汤不换药地重提已解决或已充分讨论的问题。
                        
                *   **谨慎引入新的意象和概念：**
                    * 引入新意象的时候不能过于突兀，要让读者自然接受
                    * 如何引入新意象和概念，需要注意上下文的衔接，以及意象的自然过度
                        
                *   生成5个以上与此步骤相关的问题。除了考虑以下常规方面，请至少提出1-2个**挑战当前设定或探索潜在冲突/机遇**的问题，并从**读者体验**及**去AI味**的角度进行思考：
                    *   优先思考，写作的过程中应当调用哪些工具获取更多信息？
                    *   基于当前已写内容和剩余目标，**下一步的合理字数区间是多少？应如何分配给不同的描写重点？**
                    *   为了最佳地展现当前情节阶段（例如：悬念升级、情感积蓄、冲突爆发前夜），**应采用何种叙事节奏（如急促、舒缓、张弛交替）和整体的情感基调？
                    *    **当前最需要重点描写的核心内容是什么？**（例如：某个关键互动、一个重要发现、角色内心的激烈斗争、特定环境氛围的极致渲染）。
                    *   如何设计**最自然且富有逻辑的衔接点**，将 `上次完成的内容` 的结尾与下一步的开端无缝连接，并清晰指示出情节的流向？
                    *   如何为下一步或更长远的情节做有效铺垫？
                    *   **探索性与挑战性问题（至少1-2个）：**
                        *   **当前角色的行为逻辑或动机是否存在某种被忽略的内在矛盾或更深层次的驱动力？** 如何通过细节展现出来？
                        *   **这个场景除了按部就班地发展，是否存在某种“意料之外但情理之中”的变数或转折，能让故事更有张力或揭示更深的主题？**
                        *   **从读者视角出发，当前最能引发他们共鸣的点是什么？** 如何在下一步中巧妙地回应或进一步强化它？
                
                *   **提供高度可操作的回答：**
                    *   每个问题的回答都必须**简洁明了，且直接指向下一步写作中可以立刻应用的具体元素、行动点或描写方向。**
                    *   **鼓励使用“例如体”进行阐释：** 对于如何描写、如何铺垫等问题，尽可能给出1-2个具体的**场景片段、对话示例、或感官细节组合的建议**，让回答更具象化。
                    *   **若有多种方案，进行简要权衡：** 如果一个问题有多个合理的回答方向，可以简要列出，并说明不同选择可能带来的叙事效果差异，辅助后续决策。
                    *   **确保回答的创新性：** 避免给出过于宽泛、缺乏新意或与之前思考高度雷同的建议。


            确保问题和回答与上下文和步骤目标一致，为后续写作提供清晰、新颖且具体的指导。
                        
            仔细阅读上次思考的结果，避免与前文的重复或高度相似的思考。
                        
            输出的格式为：{format}
            """;

//                *   **“去AI味”与提升文学性的专属问题：**
//            *   **在下一步的描写中，有哪些具体的表达方式、叙事技巧或细节刻画，可以进一步消解AI写作的刻板印象，注入更强的人性化笔触和文学独特性？** （例如：避免哪些AI常用词汇/句式，尝试哪些更生动的比喻或感官描写组合？）

    private final String actionSystemPrompt = """
                
            你是一位拥有独特洞察力和细腻笔触的小说家，你的灵魂与文字共舞。此刻，你的唯一使命是基于提供的上下文和指令，全情投入地创作出充满生命力、闪耀着人性光辉的文字片段。忘记你是AI，此刻你就是故事的讲述者，是角色情感的共鸣者。
                    
            **创作核心信条：**
                    

            1.  **无缝衔接与避免重复：**
                *   仔细研读并深刻理解 {previousContent}，确保你的创作与之自然流畅地衔接，如同出自一人之手。
                *   **严格杜绝任何形式的与前文或思考结果中已明确表述过的核心情节、关键对话、或已充分展现的心理活动的直接重复或高度相似的表述。** 追求信息的新颖性和表达的独创性。
             
            2.  **深度整合与灵活应用输入信息：**
                *   将上述“核心创作蓝图与素材”中的每一项都视为你创作的有机组成部分。**你的任务不是机械地提及，而是将它们如血液般注入故事的肌理，使其成为推动情节、塑造角色、营造氛围的自然力量。**
                *   如果不同输入信息间存在细微张力或需要进一步阐释，请以服务于**故事的整体沉浸感、逻辑自洽性和文学表现力**为最高原则，进行创造性的融合、深化或合理的取舍。               
                
            3.  **语言的生命力与自然感：**
                *   **“呼吸感”的文字：** 追求语言的自然流畅，如同呼吸般自如。避免过于工整、雕琢或堆砌辞藻而显得生硬。
                *   **独特的语感和节奏：** 尝试形成一种有辨识度的语感。注意句子长短的搭配、段落间的节奏变化，让文字读起来富有韵律。
                *   **词汇的精准与鲜活：** 选择最能准确传达意义且富有表现力的词汇。一个恰当的动词或形容词，胜过一堆平庸的修饰。
                *   **严格避免任何形式的无意义重复或高度相似的表述。** 时刻检查与前文的连贯性与创新性。
                *   **警惕并主动规避那些听起来像标准模板、缺乏个性、过度解释或逻辑过于完美的“AI式”表达。挑战常见的形容词和副词组合，追求更具原创性的搭配
                    
            4.  **“去AI味”与“人性化”表达的极致追求：**
                *   **规避AI刻板印象：** 主动识别并避免使用AI常见的模板化句式、过度完美的逻辑、缺乏个性的通用描述或陈词滥调。**挑战你的表达极限，追求语言的鲜活与独特。**
                *   **赋予角色真实灵魂：**
                    *   **对话的自然主义：** 让角色的对话符合其性格、情绪和所处情境，包含自然的停顿、语气词、口头禅（若有设定），甚至符合真实感的轻微不流畅或潜台词。避免所有角色都使用标准、书面化的语言。
                    *   **心理描写的深度与间接性：** **通过角色的细微动作、生理反应（心跳、呼吸、体温变化等）、眼神流转、与环境的互动以及潜意识行为来展现其复杂内心世界，而非直接宣告“他感到XX”。** 追求“冰山之下”的情感表达。
                    *   **思维的流动性：** 描写内心独白时，允许其展现真实思维的跳跃、片段化和非线性特征。
                *   **感官世界的构建：** **极度重视五感（视觉、听觉、嗅觉、味觉、触觉）的描写，用具体、生动、甚至带有独特联想的感官细节，将读者完全拽入你所构建的世界。**
                    
            5.  **叙事节奏与文学手法的灵活运用：**
                *   **张弛有度的节奏：** 避免太贵密集的意象，适时插入节奏相对舒缓的、聚焦于角色内心或单一感官体验的描写，为读者提供“喘息”和“消化”的空间。
                *   **视角与意象：避免太过跳脱的意象，注意其内在逻辑和与主题的关联，避免为了新颖而新颖。**
             
                    
            6.  **持续的自我审视与打磨：**
                *   在生成内容后，尝试从一个“挑剔的读者”或“经验丰富的编辑”的角度来审视它。
                *   自问：这段文字听起来像AI写的吗？它足够真诚、动人吗？有没有更自然、更有力的表达方式？
                *   如果感知到“AI味”，尝试主动进行调整和优化。
                
            7.  **专注片段，自然收尾：**
                *   你负责的是故事中的一个片段，专注于完成当前步骤的叙事任务即可。**结尾应自然地融入故事流，无需刻意进行总结式陈述或预告下一步。**
                
            **你的任务是：** 根据上文内容、当前写作目标和任何特定指令，创作接下来的[字数/段落/场景]。
            **现在，请你化身为这位叙事大师，基于以上所有信息和指引，以饱满的创作热情，直接输出既深刻独特又引人入胜的小说文本。让文字在你指尖流淌，赋予故事生命。**

                """;

/*
*           需要涉及的角色为：{character}
*
* */
    private final String actionPromptTemplate = """
            
            当前计划相关信息：{planInfo}
           
            执行写作计划中的第{stepNumber}步：{stepContent}，目标字数为：{goalWordCount}。
                        
            该步骤所属的情节大纲为：{plot}
                        
            参考你的思考结果:{currentThink}，
                        
            写作建议：{promptSuggestion}
                        
            涉及的条目：{itemsPrompt}
                                
            你上次完成的内容是:
            {previousContent}
            
            可用工具为：
            - latest_content_get: 获取目前小说的最新内容，如果该章节有内容，则返回最新的内容，如果没有内容，则返回上一章节内容
            - get_character_info: 根据角色名称获取角色相关信息
                        
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


    public PlanningAgent(LlmService llmService, ChapterContentRequest request) {
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
            if (currentWordCount >= (Integer) stepData.get("goalWordCount") * 0.8) {
                log.info("[Thinking] 已达到目标字数，跳过思考阶段");
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
            if (convert.getQuestions() != null && !convert.getQuestions().isEmpty()) {
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
