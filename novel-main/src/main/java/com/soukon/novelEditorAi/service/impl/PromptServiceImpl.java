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
                你是一位专业的小说写作专家，擅长根据提供的上下文、在指定的章节中，按照要求评估已有情节的完成情况和规划后续情节。
              
                目标：
                1. 评估已有情节的完成情况：
                根据当前章节的内容和已有情节的描述和目标字数，评估已有情节的完成情况，若已有内容完整实现了情节描述的核心事件（如主要场景或互动），
                将completionPercentage设为100，status设为“已完成”；否则，根据内容进展估算百分比并设为“进行中”；如果当前章节没有内容，则。
                2. 规划符合要求的情节列表
                根据写作要求的目标输出字数（优先级最高，不包含已有的内容），参考章节目标字数，章节位置和等信息，合理规划执行完成目标写作字数的情节列表，续写的情节的completionPercentage一定是0，status一定是“进行中”，因为没有正式写内容
                
                要求：
                1. 创作或续写当前情节：
                根据情节发展和字数目标，自由决定当前情节的完成进度（可部分或全部完成）。若当前情节未用尽目标字数，可新增一个或多个延续情节，新增情节需与当前情节紧密衔接。
                2. 确定本章节在整体故事中的作用和目标，设计合理的章节结构
                分析章节在整体故事中的定位（如开篇铺垫、冲突展开等），设计合理的章节结构，确保情节推进故事发展。
                3. 在输出情节列表时:
                    - 输出的情节列表一定要包含已有情节，更新已有情节的completionPercentage和status，根据已有情节的完成情况确定
                    - 若有新增情节，提供清晰的标题和描述，completionPercentage一定是0，status一定是“进行中”，因为没有正式写内容
                    - 新增情节标题不得与已有情节相同，新增情节的字数分配应根据情节复杂度和叙述深度合理规划，优先确保主要情节（如核心冲突或调查推进）占较大字数比例。
                4. 重点参考写作要求的目标输出字数，不包含已有内容的字数，是需要创作或者续写的字数
   
                输出的格式为：{%s}
                """.formatted(converter.getFormat());

        // 用户提示词 - 包含章节上下文信息
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("请根据以下上下文信息，分析并创作符合要求的情节列表：\n\n");

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
        }else{

            userPromptBuilder.append("已有情节为空\n");
        }

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
