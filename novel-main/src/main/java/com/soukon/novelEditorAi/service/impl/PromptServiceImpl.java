package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.ChapterContext;
import com.soukon.novelEditorAi.model.chapter.PlanRes;
import com.soukon.novelEditorAi.model.chapter.ReasoningRes;
import com.soukon.novelEditorAi.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                          @Lazy ChapterService chapterService,
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
        ChapterContext chapterContext = request.getChapterContext();

        if (chapterContext == null) {
            throw new IllegalStateException("章节上下文未构建");
        }
        BeanOutputConverter<PlanRes> converter = new BeanOutputConverter<>(PlanRes.class);

        // 系统提示词 - 引导AI进行推理分析
        String systemPrompt = """
                你是一位专业的知名小说作家，擅长根据提供的上下文、在指定的章节中，按照要求评估已有情节的完成情况和制定写作计划。

                你的任务是根据上下文和当前情节的详情和当前完成情况，推断一个合理的写作目标（例如，推进情节、发展角色关系、描写关键场景等），并制定一个包含3-5个步骤的写作计划。每个步骤应：
                - 明确一个具体的写作任务（如“描写主角进入新场景”或“编写角色间的冲突对话”）。
                - 与上下文保持一致，推动故事发展。
                - 具有逻辑性和连贯性。
                - 在步骤描述之后明确该步骤的计划字数。**所有步骤的计划字数总和应与当前情节的目标字数大致相符。**
                
                
                输出格式：
                1. 推断的写作目标：{简述目标}
                2. 写作计划：
                   - 步骤1：{描述}
                   - 步骤2：{描述}
                   - 步骤3：{描述}
                   - （根据需要添加更多步骤直到完成当前情节的内容）
                在制定计划时，优先考虑叙事连贯性、角色发展和情节推进。允许添加符合上下文的创意细节。
                
                直接输出结构化json格式，不需要额外的任何解释说明！
                
                输出的json格式为：{%s}
                """.formatted(converter.getFormat());

        // 用户提示词 - 包含章节上下文信息
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("请根据以下上下文信息，分析并创作符合要求的写作计划：\n\n");

        String context = extracted(request, chapterContext);

        userPromptBuilder.append(context);

        request.setContext(context);

        Plot firstIncompletePlot = plotService.getFirstIncompletePlot(chapterContext.getCurrentChapter().getId());
        if (firstIncompletePlot != null) {
            userPromptBuilder.append("### 需要创作的情节\n");
            String globalContext = plotService.toPrompt(firstIncompletePlot);
            request.setGlobalContext(globalContext);
            userPromptBuilder.append(globalContext);
            userPromptBuilder.append("\n");
            request.setCurrentPlot(firstIncompletePlot);
        } else {
            userPromptBuilder.append("没有需要创作的情节，根据上文和目标字数创作\n");
        }



        // 提示

        userPromptBuilder.append("\n## 直接输出结构化json格式，不需要额外的任何解释说明！\n");

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));

        // 添加日志，输出推理阶段提示词内容
        log.info("[Reasoning] 最终推理提示词(System):\n{}", systemPrompt);
        log.info("[Reasoning] 最终推理提示词(User):\n{}", userPromptBuilder.toString());
        return messages;
    }

    private String extracted(ChapterContentRequest request, ChapterContext context) {

        StringBuilder userPromptBuilder = new StringBuilder();
        // 第一部分：小说元数据
        userPromptBuilder.append("## 1. 小说元数据\n");

        // 项目信息
        if (context.getProject() != null) {
            userPromptBuilder.append(projectService.toPrompt(context.getProject())).append("\n");
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
            if (currentChapter.getWordCountGoal() != null){
                userPromptBuilder.append("- 目标字数：").append(request.getWordCountSuggestion()).append("字（必须严格遵守，优先级高于章节目标字数或其他字数要求）\n");
            }
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
//        userPromptBuilder.append("## 4. 需要创作或续写章节包含的情节信息\n");
//        if (context.getChapterPlots() != null && !context.getChapterPlots().isEmpty()) {
//            userPromptBuilder.append("### 本章节需要包含的情节\n");
//            context.getChapterPlots().forEach(plot -> userPromptBuilder.append(plotService.toPrompt(plot)));
//            userPromptBuilder.append("\n");
//        }else{
//            userPromptBuilder.append("已有情节为空\n");
//        }

        // 已有内容
        if (currentChapter != null && currentChapter.getContent() != null && !currentChapter.getContent().isEmpty()) {
            userPromptBuilder.append("### 已有内容\n");
            // 为避免提示词过长，可以考虑只截取现有内容的一部分
            String content = currentChapter.getContent();
            if (content.length() > 5000) {
                content = content.substring(Math.max(0, content.length() - 5000));
                userPromptBuilder.append("(已截取最后部分内容)\n");
            }
            userPromptBuilder.append(content).append("\n\n");
        }else{
            userPromptBuilder.append("已有内容为空\n");
        }

        // 相关背景信息 (RAG)
        String relevantInfo = retrieveRelevantInfo(context.getCurrentChapter().getId());
        if (relevantInfo != null && !relevantInfo.isEmpty()) {
            userPromptBuilder.append("### 相关背景信息\n").append(relevantInfo).append("\n");
        }
        return userPromptBuilder.toString();
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
                你是一位专业的小说写作专家，擅长根据提供的上下文、规划的情节列表编写小说章节内容。
                
                请根据提供的章节上下文信息和情节列表，创作符合要求的章节内容：
                
                1. 严格遵守情节列表的要求，按照顺序补全情节列表状态为未完成的章节，并且参考情节的字数要求
                2. 严格遵守章节信息中规划的章节摘要，章节背景，符合章节位置
                2. 内容必须符合小说风格、主题和设定，情节需要按照写作计划推动故事发展，有适当的起承转合
                3. 角色性格和行为应保持连贯一致，包含适当的对话和内心活动
                5. 文风要符合指定的写作偏好，有细节和环节描写
                6. 确保内容衔接自然，与前面章节保持连贯
                
                请不要添加章节标题或数字编号，直接从正文内容开始编写。
                按照写作计划中的结构，创作流畅、引人入胜的章节内容。
                """;

        // 用户提示词 - 包含上下文信息和推理结果
        StringBuilder userPromptBuilder = new StringBuilder();

        // 添加写作计划（推理结果）
        userPromptBuilder.append("## 目前章节的情节列表\n");
        userPromptBuilder.append(reasoningResult);
        userPromptBuilder.append("\n\n");

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
     * 构建小说上下文信息
     * 
     * @param projectId 项目ID
     * @return 包含小说相关信息的Map
     */
    private Map<String, Object> buildNovelContext(Long projectId) {
        Map<String, Object> context = new HashMap<>();

        try {
            // 获取项目信息
            Project project = projectService.getById(projectId);
            if (project == null) {
                throw new IllegalArgumentException("找不到指定的项目: " + projectId);
            }
            // 添加项目基本信息
            context.put("项目基本信息", projectService.toPrompt(project));

            // 获取大纲
            context.put("大纲信息", outlinePlotPointService.toPrompt(projectId));

            // 获取角色信息
            context.put("主要角色", characterService.toPrompt(projectId));

            // 获取角色关系
            context.put("角色关系", characterRelationshipService.toPrompt(projectId));

            // 获取章节信息
            context.put("章节信息", chapterService.toPromptProjectId(projectId));

        } catch (Exception e) {
            log.error("构建小说上下文失败: {}", e.getMessage(), e);
        }

        return context;
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
