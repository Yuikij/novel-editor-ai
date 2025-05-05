package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.ChapterContext;
import com.soukon.novelEditorAi.model.chapter.ReasoningRes;
import com.soukon.novelEditorAi.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词服务实现类，负责生成与AI交互的提示词
 */
@Service
@Slf4j
public class PromptServiceImpl implements PromptService {

    private final ProjectService projectService;
    private final ChapterService chapterService;
    private final WorldService worldService;
    private final CharacterService characterService;
    private final PlotService plotService;
    private final CharacterRelationshipService characterRelationshipService;
    private final OutlinePlotPointService outlinePlotPointService;
    private final RagService ragService;

    @Value("${novel.rag.max-results:5}")
    private Integer ragMaxResults;

    public PromptServiceImpl(ProjectService projectService,
                          ChapterService chapterService,
                          WorldService worldService,
                          CharacterService characterService,
                          PlotService plotService,
                          CharacterRelationshipService characterRelationshipService,
                          OutlinePlotPointService outlinePlotPointService,
                          RagService ragService) {
        this.projectService = projectService;
        this.chapterService = chapterService;
        this.worldService = worldService;
        this.characterService = characterService;
        this.plotService = plotService;
        this.characterRelationshipService = characterRelationshipService;
        this.outlinePlotPointService = outlinePlotPointService;
        this.ragService = ragService;
    }

    /**
     * 构建推理阶段的提示词
     * 用于分析章节要求并制定写作计划
     */
    @Override
    public List<Message> buildReasoningPrompt(ChapterContentRequest request) {
        ChapterContext context = request.getChapterContext();

        if (context == null) {
            throw new IllegalStateException("章节上下文未构建");
        }
        BeanOutputConverter<ReasoningRes> converter = new BeanOutputConverter<>(ReasoningRes.class);

        // 系统提示词 - 引导AI进行推理分析
        String systemPrompt = """
                你是一位专业的小说分析和规划专家，擅长分析写作需求并制定写作计划。
              
                你将根据以下上下文。制定一个详细的章节写作计划：
                1. 小说元数据，包括小说标题、类型、结构类型、小说风格、目标字数、亮点、写作要求、世界观、主要角色、角色关系、小说整体大纲等信息
                2. 写作要求：需要创作或续写的字数、写作建议
                3. 当前的写作进度和需要创作或续写的章节信息，包括章节的标题、类型、目标字数、摘要、背景、该章节位于全篇的第几章，该章节的目标字数、该章节已有内容，上章节概要
                4. 需要创作或续写章节包含的情节信息，包括情节的概述和情节的完成情况
                
                计划要求：
                1. 仔细分析小说背景、角色、世界观和已有情节
                2. 识别关键的情节要素、冲突点和情感线索
                3. 找到当前正在写作的情节，续写并且酌情添加情节
                3. 确定本章节在整体故事中的作用和目标
                4. 设计合理的章节结构，包括起承转合
                5. 制定角色对话、情节推进和环境描写的计划
                6. 分析上下文中提到的特定写作风格和偏好
                
               
                请先思考，然后输出一个结构化的章节写作计划，包括：
                - 内容一定要和当前的情节相吻合，如果目标字数过大可以新增情节，新增情节是当前情节完成后的延续
                - 根据章节字数和目标输出字数，合理规划当前输出内容
                - 核心主题和目标
                - 情节架构和节奏
                - 关键场景设计
                - 角色互动计划
                - 冲突和转折点设计
                你的分析和计划将作为下一步实际写作的指导。
                
                然后输出写作完成之后的情节列表，注意：
                - 根据情节的完成情况和完成百分比，以及需要续写或创作的字数要求，输出计划后的情节列表
                - 只能修改已有的情节的完成百分比和完成情况，但是要考虑当前所属章节情况，务必局限在本章
                - 如果目标字数过大可以新增情节，新增情节是当前情节完成后的延续
                - 新增情节不能和之前情节的标题相同
                - 理论上只能有一个情节的完成百分比不是100
                - 输出的情节完成情况的百分比要与计划相符
                
                输出的格式为：{%s}
                """.formatted(converter.getFormat());

        // 用户提示词 - 包含章节上下文信息
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("请根据以下上下文信息，分析并制定本章节的写作计划：\n\n");

        // 第一部分：小说元数据
        userPromptBuilder.append("## 1. 小说元数据\n");
        
        // 项目信息
        if (context.getProject() != null) {
            userPromptBuilder.append(projectService.toPrompt(context.getProject())).append("\n");
        }
        
        // 世界观信息
        if (context.getWorld() != null) {
            userPromptBuilder.append("### 世界观信息\n");
            userPromptBuilder.append(worldService.toPrompt(context.getWorld())).append("\n");
        }
        
        // 角色信息
        if (context.getCharacters() != null && !context.getCharacters().isEmpty()) {
            userPromptBuilder.append("### 主要角色\n");
            context.getCharacters().forEach(character -> userPromptBuilder.append(characterService.toPrompt(character)));
            userPromptBuilder.append("\n");
        }
        
        // 角色关系信息
        if (context.getCharacterRelationships() != null && !context.getCharacterRelationships().isEmpty()) {
            userPromptBuilder.append("### 角色关系\n");
            context.getCharacterRelationships().forEach(rel -> userPromptBuilder.append(characterRelationshipService.toPrompt(rel)));
            userPromptBuilder.append("\n");
        }
        
        // 大纲情节点信息
        if (context.getPlotPoints() != null && !context.getPlotPoints().isEmpty()) {
            userPromptBuilder.append("### 小说整体大纲\n");
            context.getPlotPoints().forEach(point -> userPromptBuilder.append(outlinePlotPointService.toPrompt(point)));
            userPromptBuilder.append("\n");
        }

        // 第二部分：写作目标
        userPromptBuilder.append("## 2. 写作目标\n");

        // 添加写作目标的具体要求
        Chapter currentChapter = context.getCurrentChapter();
        if (currentChapter != null) {
            userPromptBuilder.append("### 写作要求\n");
            userPromptBuilder.append("- 目标字数：").append(request.getWordCountSuggestion()).append("字\n");
            if (currentChapter.getContent() != null && !currentChapter.getContent().isEmpty()) {
                userPromptBuilder.append("- 类型：续写\n");
            } else {
                userPromptBuilder.append("- 类型：创作\n");
            }
            userPromptBuilder.append("- 写作建议：").append(request.getPromptSuggestion()).append("\n");

            userPromptBuilder.append("\n");
        }

        // 第三部分：当前写作进度和章节信息
        userPromptBuilder.append("## 3. 当前写作进度和章节信息\n");
        
        // 章节信息
        String previousChapterSummary = context.getPreviousChapter() != null ? context.getPreviousChapter().getSummary() : null;
        if (currentChapter != null) {
            userPromptBuilder.append(chapterService.toPrompt(currentChapter, previousChapterSummary));
            userPromptBuilder.append("\n");
        }

        //  第四部分  需要创作或续写章节包含的情节信息，包括情节的概述和情节的完成情况  
        userPromptBuilder.append("## 4. 需要创作或续写章节包含的情节信息\n");
        if (context.getChapterPlots() != null && !context.getChapterPlots().isEmpty()) {
            userPromptBuilder.append("### 本章节需要包含的情节\n");
            context.getChapterPlots().forEach(plot -> userPromptBuilder.append(plotService.toPrompt(plot)));
            userPromptBuilder.append("\n");
        }
        
        // 已有内容
        if (currentChapter != null && currentChapter.getContent() != null && !currentChapter.getContent().isEmpty()) {
            userPromptBuilder.append("### 已有内容\n");
            // 为避免提示词过长，可以考虑只截取现有内容的一部分
            String content = currentChapter.getContent();
            if (content.length() > 1000) {
                content = content.substring(Math.max(0, content.length() - 1000));
                userPromptBuilder.append("(已截取最后部分内容)\n");
            }
            userPromptBuilder.append(content).append("\n\n");
        }

        // 相关背景信息 (RAG)
        String relevantInfo = retrieveRelevantInfo(context.getCurrentChapter().getId());
        if (relevantInfo != null && !relevantInfo.isEmpty()) {
            userPromptBuilder.append("### 相关背景信息\n").append(relevantInfo).append("\n");
        }

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));

        // 添加日志，输出推理阶段提示词内容
        log.info("[Reasoning] 最终推理提示词(System):\n{}", systemPrompt);
        log.info("[Reasoning] 最终推理提示词(User):\n{}", userPromptBuilder.toString());
        return messages;
    }

    /**
     * 构建执行阶段的提示词
     * 用于根据推理结果生成章节内容
     */
    @Override
    public List<Message> buildActingPrompt(ChapterContentRequest request, String reasoningResult) {
        ChapterContext context = request.getChapterContext();
        if (context == null) {
            throw new IllegalStateException("章节上下文未构建");
        }

        // 系统提示词 - 引导AI进行创作
        String systemPrompt = """
                你是一位专业的小说写作专家，擅长根据提供的上下文、规划和要求编写小说章节内容。
                
                请根据提供的章节上下文信息和写作计划，创作符合以下标准的章节内容：
                1. 内容必须符合小说风格、主题和设定
                2. 情节需要按照写作计划推动故事发展，有适当的起承转合
                3. 角色性格和行为应保持连贯一致
                4. 章节应包含适当的描写、对话和内心活动
                5. 文风要符合指定的写作偏好
                6. 确保内容衔接自然，与前面章节保持连贯
                7. 如果提供了现有内容，需要自然地衔接和扩展
                
                请不要添加章节标题或数字编号，直接从正文内容开始编写。
                按照写作计划中的结构，创作流畅、引人入胜的章节内容。
                """;

        // 用户提示词 - 包含上下文信息和推理结果
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("请根据以下章节上下文和写作计划，创作本章节内容：\n\n");

        // 添加写作计划（推理结果）
        userPromptBuilder.append("## 写作计划与分析\n");
        userPromptBuilder.append(reasoningResult);
        userPromptBuilder.append("\n\n");

        // 添加简要的上下文信息（比推理阶段更精简）
        userPromptBuilder.append("## 核心上下文信息\n");

        // 项目和章节基本信息（简要版）
        if (context.getProject() != null) {
            userPromptBuilder.append("- 小说标题: ").append(context.getProject().getTitle()).append("\n");
            userPromptBuilder.append("- 小说类型: ").append(context.getProject().getGenre()).append("\n");
        }

        if (context.getCurrentChapter() != null) {
            userPromptBuilder.append("- 当前章节: ").append(context.getCurrentChapter().getTitle()).append("\n");
            if (context.getCurrentChapter().getSummary() != null) {
                userPromptBuilder.append("- 章节概要: ").append(context.getCurrentChapter().getSummary()).append("\n");
            }
        }

        // 现有内容（续写）
        String existingContent = context.getCurrentChapter() != null ? context.getCurrentChapter().getContent() : null;
        if (existingContent != null && !existingContent.trim().isEmpty()) {
            userPromptBuilder.append("\n## 已有内容（需要续写）\n").append(existingContent).append("\n\n");
            userPromptBuilder.append("请根据上述信息，按照写作计划续写本章节内容。\n");
        } else {
            userPromptBuilder.append("\n请根据上述信息，按照写作计划创作本章节内容。\n");
        }

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));

        // 添加日志，输出执行阶段提示词内容
        log.info("[Acting] 最终执行提示词(System):\n{}", systemPrompt);
        log.info("[Acting] 最终执行提示词(User):\n{}", userPromptBuilder.toString());
        return messages;
    }

    /**
     * 检索与章节相关的信息
     */
    private String retrieveRelevantInfo(Long chapterId) {
        try {
            // 使用RAG服务检索相关文档
            List<Document> relevantDocs = ragService.retrieveRelevantForChapter(chapterId, ragMaxResults);

            if (relevantDocs == null || relevantDocs.isEmpty()) {
                log.info("没有找到与章节 {} 相关的文档", chapterId);
                return null;
            }

            // 提取文档内容并格式化
            StringBuilder relevantInfo = new StringBuilder();
            for (Document doc : relevantDocs) {
                String docType = doc.getMetadata().getOrDefault("type", "unknown").toString();

                relevantInfo.append("- ");
                switch (docType) {
                    case "chapter":
                        relevantInfo.append("章节「")
                                .append(doc.getMetadata().getOrDefault("title", ""))
                                .append("」: ");
                        break;
                    case "character":
                        relevantInfo.append("角色「")
                                .append(doc.getMetadata().getOrDefault("name", ""))
                                .append("」: ");
                        break;
                    case "world":
                        relevantInfo.append("世界观「")
                                .append(doc.getMetadata().getOrDefault("name", ""))
                                .append("」: ");
                        break;
                    default:
                        relevantInfo.append(docType).append(": ");
                }

                // 添加文档内容的摘要
                String content = doc.getText();
                if (content.length() > 150) {
                    content = content.substring(0, 150) + "...";
                }
                relevantInfo.append(content).append("\n\n");
            }

            return relevantInfo.toString();
        } catch (Exception e) {
            log.warn("检索相关信息失败", e);
            return null;
        }
    }
}
