package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Project;
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
        ChapterContext context = request.getChapterContext();

        if (context == null) {
            throw new IllegalStateException("章节上下文未构建");
        }
        BeanOutputConverter<ReasoningRes> converter = new BeanOutputConverter<>(ReasoningRes.class);

        // 系统提示词 - 引导AI进行推理分析
        String systemPrompt = """
                你是一位专业的小说写作专家，擅长根据提供的上下文、规划和要求规划小说章节内容。
              
                请根据提供的章节上下文信息，创作符合要求的情节列表和章节内容：
                
                目标：
                1. 根据章节信息的章节字数，章节位置和写作要求的目标输出字数，以及已经当前的情节列表等信息，合理规划执行完成目标写作字数的情节列表
                2. 根据小说元数据和规划后情节列表，输出具体章节内容
                
                
                情节列表要求：
                1. 创作或续写当前情节，根据情节发展和字数目标，自由决定当前情节的完成进度（可部分或全部完成）。若当前情节未用尽目标字数，可新增一个或多个延续情节，新增情节需与当前情节紧密衔接。
                2. 确定本章节在整体故事中的作用和目标，设计合理的章节结构
                3. 在输出情节列表时:
                    - 更新当前情节的完成情况和百分比，根据写作计划自由确定（例如100表示完成，低于100表示部分完成）。
                    - 若有新增情节，提供清晰的标题和描述，状态为"进行中"或"已完成"，并自由确定完成百分比。
                    - 确保只有一个情节的完成百分比非100，以支持后续续写。新增情节标题不能与当前已有情节相同。
                    - 每个情节的完成百分比需与写作计划中的字数分配和内容进展一致。例如，若当前情节分配3000字且全部完成，则将当前情节的完成百分比字段设为100；若新增情节分配2000字且部分完成，设为50-80。
                4. 更新当前情节及其他情节的`completionPercentage`和`status`，根据写作计划的字数分配和内容进展确定。
                5. 新增情节需提供清晰的标题和描述，状态为"进行中"或"已完成"。
                
                章节内容要求：
                1. 内容必须符合小说风格、主题和设定
                2. 情节需要按照写作计划推动故事发展，有适当的起承转合
                3. 角色性格和行为应保持连贯一致，制定角色对话、情节推进和环境描写的计划
                4. 章节应包含适当的描写、对话和内心活动
                5. 文风要符合指定的写作偏好，
                6. 确保内容衔接自然，与前面章节保持连贯
                7. 如果提供了现有内容，需要自然地衔接和扩展
                8. 仔细分析小说背景、角色、世界观和已有情节，识别关键的情节要素、冲突点和情感线索

                请不要添加章节标题或数字编号，直接从正文内容开始编写。
                按照写作计划中的结构，创作流畅、引人入胜的章节内容。

                输出的格式为：{%s}
                """.formatted(converter.getFormat());

        // 用户提示词 - 包含章节上下文信息
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("请根据以下上下文信息，分析并创作符合要求的情节列表和章节内容：\n\n");

        extracted(request, userPromptBuilder, context);

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));

        // 添加日志，输出推理阶段提示词内容
        log.info("[Reasoning] 最终推理提示词(System):\n{}", systemPrompt);
        log.info("[Reasoning] 最终推理提示词(User):\n{}", userPromptBuilder.toString());
        return messages;
    }

    private void extracted(ChapterContentRequest request, StringBuilder userPromptBuilder, ChapterContext context) {
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
            userPromptBuilder.append("- 目标字数：").append(request.getWordCountSuggestion()).append("字（必须严格遵守，优先级高于章节目标字数或其他字数要求）\n");
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
                
                请根据提供的章节上下文信息和情节列表，创作符合要求的章节内容：
                
                1. 严格遵守情节列表的要求
                
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

        extracted(request, userPromptBuilder, context);

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
