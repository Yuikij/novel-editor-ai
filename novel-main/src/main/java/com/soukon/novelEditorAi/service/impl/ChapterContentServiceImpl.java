package com.soukon.novelEditorAi.service.impl;

import com.alibaba.nacos.common.utils.UuidUtils;
import com.soukon.novelEditorAi.agent.AgentState;
import com.soukon.novelEditorAi.agent.WritingAgent;
import com.soukon.novelEditorAi.agent.EnhancedWritingAgent;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.entities.CharacterRelationship;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.mapper.ChapterMapper;
import com.soukon.novelEditorAi.mapper.CharacterMapper;
import com.soukon.novelEditorAi.mapper.PlotMapper;
import com.soukon.novelEditorAi.mapper.ProjectMapper;
import com.soukon.novelEditorAi.mapper.WorldMapper;
import com.soukon.novelEditorAi.model.chapter.*;
import com.soukon.novelEditorAi.service.*;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.soukon.novelEditorAi.model.chapter.ReasoningRes;
import org.springframework.ai.converter.BeanOutputConverter;

import static com.soukon.novelEditorAi.model.chapter.PlanState.IN_PROGRESS;

/**
 * 章节内容服务实现类
 * 负责章节内容的生成、保存和检索
 */
@Service
@Slf4j
public class ChapterContentServiceImpl implements ChapterContentService {

    private final ChatClient chatClient;
    private final ProjectMapper projectMapper;
    private final ChapterMapper chapterMapper;
    private final WorldMapper worldMapper;
    private final CharacterMapper characterMapper;
    private final PlotMapper plotMapper;
    private final RagService ragService;
    private final PromptService promptService;
    private final ProjectService projectService;
    private final ChapterService chapterService;
    private final WorldService worldService;
    private final CharacterService characterService;
    private final PlotService plotService;
    private final CharacterRelationshipService characterRelationshipService;
    private final OutlinePlotPointService outlinePlotPointService;
    @Autowired
    private ItemService itemService;

    @Getter
    private ConcurrentHashMap<String, PlanContext> planContextMap = new ConcurrentHashMap<>();


    @Autowired
    private LlmService llmService;

    @Value("${novel.chapter.default-max-tokens:2000}")
    private Integer defaultMaxTokens;


    @Value("${novel.chapter.default-temperature:0.7}")
    private Float defaultTemperature;

    @Value("${novel.rag.max-results:5}")
    private Integer ragMaxResults;

    @Value("${novel.rag.enabled:true}")
    private Boolean ragEnabled;

    @Autowired
    public ChapterContentServiceImpl(ChatModel openAiChatModel,
                                     ProjectMapper projectMapper,
                                     ChapterMapper chapterMapper,
                                     WorldMapper worldMapper,
                                     CharacterMapper characterMapper,
                                     PlotMapper plotMapper,
                                     RagService ragService,
                                     @Lazy PromptService promptService,
                                     ProjectService projectService,
                                     ChapterService chapterService,
                                     WorldService worldService,
                                     CharacterService characterService,
                                     PlotService plotService,
                                     CharacterRelationshipService characterRelationshipService,
                                     OutlinePlotPointService outlinePlotPointService) {
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .topP(0.7)
                                .build()
                )
                .build();
        this.projectMapper = projectMapper;
        this.chapterMapper = chapterMapper;
        this.worldMapper = worldMapper;
        this.characterMapper = characterMapper;
        this.plotMapper = plotMapper;
        this.ragService = ragService;
        this.promptService = promptService;
        this.projectService = projectService;
        this.chapterService = chapterService;
        this.worldService = worldService;
        this.characterService = characterService;
        this.plotService = plotService;
        this.characterRelationshipService = characterRelationshipService;
        this.outlinePlotPointService = outlinePlotPointService;
    }

    @Override
    public boolean saveChapterContent(Long chapterId, String content, Boolean appendMode) {
        try {
            if (content == null) {
                log.warn("保存章节内容失败：内容为空");
                return false;
            }

            // 检查内容长度
            if (content.length() > 100000) {
                log.warn("章节内容过长，已截断至 100000 字符");
                content = content.substring(0, 100000);
            }

            Chapter chapter = chapterMapper.selectById(chapterId);
            if (chapter != null) {
                // 根据模式决定是追加还是覆盖内容
                if (appendMode != null && appendMode) {
                    // 追加模式
                    String existingContent = chapter.getContent();
                    if (existingContent != null && !existingContent.trim().isEmpty()) {
                        // 在已有内容后追加，添加两个换行作为分隔
                        chapter.setContent(existingContent + "\n\n" + content);
                        log.info("追加内容到章节 {}", chapterId);
                    } else {
                        // 如果原内容为空，直接设置
                        chapter.setContent(content);
                        log.info("章节 {} 无原有内容，直接保存新内容", chapterId);
                    }
                } else {
                    // 覆盖模式
                    chapter.setContent(content);
                    log.info("覆盖章节 {} 的内容", chapterId);
                }

                // 更新字数统计
                chapter.setWordCount((long) countWords(chapter.getContent()));

                // 更新到数据库
                chapterMapper.updateById(chapter);

                // 更新索引，使用异步方式
                if (ragEnabled) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            ragService.indexChapter(chapterId);
                        } catch (OutOfMemoryError e) {
                            log.error("索引章节内容时发生内存溢出: {}", e.getMessage());
                        } catch (Exception e) {
                            log.warn("索引章节内容失败: {}", e.getMessage());
                        }
                    });
                }

                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("保存章节内容失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Result<String> generateChapterContentExecute(ChapterContentRequest request) {
        String planId = UuidUtils.generateUuid();

        // 创建新的计划上下文
        PlanContext planContext = new PlanContext(planId);
        planContext.setPlanState(PlanState.PLANNING);
        planContext.setMessage("正在执行章节内容生成计划");
        planContext.setProgress(0);
        planContextMap.put(planId, planContext);
        request.setPlanContext(planContext);

        // 异步执行任务
        CompletableFuture.runAsync(() -> {
            try {
                planContext.setPlanState(PlanState.PLANNING);
                // 生成内容
                generateChapterContentStreamFlux(request);
                // 更新计划状态
                planContext.setPlanState(PlanState.COMPLETED);
                planContext.setMessage("执行计划结束");
                planContext.setProgress(100);
            } catch (Exception e) {
                log.error("执行计划失败", e);
                planContext.setPlanState(PlanState.COMPLETED);  // 即使失败也标记为完成
                throw new RuntimeException("执行计划失败: " + e.getMessage(), e);
            }
        });
        return Result.success("执行成功", planId);
    }

    /**
     * 验证请求参数
     */
    private void validateRequest(ChapterContentRequest request) {
        if (request.getChapterId() == null) {
            throw new IllegalArgumentException("章节ID不能为空");
        }
    }

    /**
     * 构建章节上下文信息
     */
    private ChapterContext buildChapterContext(Long chapterId) {
        // 获取章节信息
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("找不到指定的章节: " + chapterId);
        }

        Long projectId = chapter.getProjectId();

        // 获取项目信息
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("找不到指定的项目: " + projectId);
        }

        // 构建章节上下文
        ChapterContext.ChapterContextBuilder contextBuilder = ChapterContext.builder()
                .project(project)
                .currentChapter(chapter)
                .projectId(projectId)
                .novelTitle(project.getTitle())
                .novelSummary(project.getSynopsis())
                .novelStyle(project.getStyle())
                .chapterSummary(chapter.getSummary());

        // 获取世界观信息
        World world = worldMapper.selectById(project.getWorldId());
        if (world != null) {
            contextBuilder.world(world);
        }

        // 获取角色信息
        List<Character> characters = characterMapper.selectListByProjectId(projectId);
        if (characters != null && !characters.isEmpty()) {
            contextBuilder.characters(characters);
        }

        // 获取角色关系
        List<CharacterRelationship> relationships = characterRelationshipService.getByProjectId(projectId);
        if (relationships != null && !relationships.isEmpty()) {
            contextBuilder.characterRelationships(relationships);
        }

        // 获取前一章节
        if (chapter.getSortOrder() > 1) {
            Chapter previousChapter = chapterMapper.selectByProjectIdAndOrder(
                    projectId, chapter.getSortOrder() - 1);
            if (previousChapter != null) {
                contextBuilder.previousChapter(previousChapter);
                contextBuilder.previousChapterSummary(previousChapter.getSummary());
            }
        }

        // 获取下一章节
        Chapter nextChapter = chapterMapper.selectByProjectIdAndOrder(
                projectId, chapter.getSortOrder() + 1);
        if (nextChapter != null) {
            contextBuilder.nextChapterSummary(nextChapter.getSummary());
        }

        // 获取章节关联的情节
        List<Plot> plots = plotMapper.selectListByChapterId(chapterId);
        if (plots != null && !plots.isEmpty()) {
            contextBuilder.chapterPlots(plots);
        }

        return contextBuilder.build();
    }

    /**
     * 计算内容字数
     */
    private Integer countWords(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 对于中文，计算字符数；对于英文，计算单词数
        // 这里简单处理，实际应用中可能需要更复杂的逻辑
        return content.length();
    }

    @Override
    public void generateChapterContentStreamFlux(ChapterContentRequest request) {

        // 第一阶段：Reasoning - 理解和分析需求
        log.info("[Enhanced Reasoning] 开始分析章节内容生成需求");

        // 验证请求参数
        validateRequest(request);

        // 查询并构建章节上下文
        ChapterContext context = buildChapterContext(request.getChapterId());
        request.setChapterContext(context);

        // 设置默认参数
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(defaultMaxTokens);
        } else if (request.getMaxTokens() > 4000) {
            log.warn("[Enhanced Reasoning] 请求的 token 数过大，已限制为 4000");
            request.setMaxTokens(4000);
        }
        if (request.getTemperature() == null) {
            request.setTemperature(defaultTemperature);
        }

        // 使用增强版提示词服务构建计划提示词
        EnhancedPromptServiceImpl enhancedPromptService = new EnhancedPromptServiceImpl(
                this.projectService,
                this.chapterService,
                this.worldService,
                this.characterService,
                this.plotService,
                this.characterRelationshipService,
                this.outlinePlotPointService
        );

        List<Message> reasoningMessages = enhancedPromptService.buildEnhancedPlanningPrompt(request);

        // 执行推理过程，分析章节要求并制定写作计划
        String reasoningResult;
        PlanRes planRes;
        PlanContext planContext = request.getPlanContext();
        try {
            BeanOutputConverter<PlanRes> converter = new BeanOutputConverter<>(PlanRes.class);
            log.info("[Enhanced Reasoning] 正在分析章节要求并制定高质量写作计划:{}", reasoningMessages);

            reasoningResult = llmService.getAgentChatClient(planContext.getPlanId()).getChatClient()
                    .prompt(new Prompt(reasoningMessages)).call().content();
            log.info("[Enhanced Reasoning] 完成章节分析和写作计划原数据: {}", reasoningResult);
            planRes = reasoningResult == null ? null : converter.convert(reasoningResult);
            log.info("[Enhanced Reasoning] 完成章节分析和写作计划: {}", planRes);
        } catch (Exception e) {
            log.error("[Enhanced Reasoning] 章节分析失败: {}", e.getMessage(), e);
            throw new RuntimeException("分析章节要求失败：" + e.getMessage(), e);
        }
        if (planRes == null) {
            log.error("[Enhanced Reasoning] 章节分析结果为空");
            throw new RuntimeException("分析章节要求失败：结果为空");
        }

        // 将planRes的completePercent保存到数据库
        Plot plot = request.getCurrentPlot();
        if (!request.isFreedom()) {
            plot.setCompletionPercentage(100);
            request.setWordCountSuggestion(plot.getWordCountGoal());
            plotService.updateById(plot);
        }

        // 第二阶段：Enhanced Acting - 使用增强版写作代理执行写作
        log.info("[Enhanced Acting] 开始使用增强版写作代理生成章节内容");
        List<PlanDetailRes> planList = planRes.getPlanList();
        if (planList == null || planList.isEmpty()) {
            log.error("[Enhanced Acting] 章节计划列表为空");
            throw new RuntimeException("生成章节内容失败：计划列表为空");
        }

        // 使用增强版WritingAgent执行写作计划
        log.info("[Enhanced Acting] 使用增强版写作代理生成章节内容，计划步骤数: {}", planList.size());

        // 创建增强版写作代理
        EnhancedWritingAgent enhancedWritingAgent = new EnhancedWritingAgent(this.llmService, request);

        planContext.setPlanState(PlanState.IN_PROGRESS);
        planContext.setMessage("已完成高质量计划设计，总目标：" + planRes.getGoal());
        planContext.setProgress(10);
        request.setPlan(planRes.toString());

        // 转换PlanDetailRes列表
        List<EnhancedWritingAgent.PlanDetailRes> enhancedPlanList = new ArrayList<>();
        for (PlanDetailRes originalPlan : planList) {
            enhancedPlanList.add(new EnhancedWritingAgent.PlanDetailRes(
                    originalPlan.getGoalWordCount(),
                    originalPlan.getPlanContent()
            ));
        }

        // 执行增强版写作计划
        try {
            enhancedWritingAgent.executeWritingPlan(enhancedPlanList);
        } catch (Exception e) {
            log.error("[Enhanced Acting] 增强版写作执行失败", e);
            planContext.setPlanState(PlanState.COMPLETED);
            planContext.setMessage("写作失败：" + e.getMessage());
            throw new RuntimeException("增强版写作执行失败", e);
        }

        llmService.removeAgentChatClient(planContext.getPlanId());
    }
} 