package com.soukon.novelEditorAi.service.impl;

import com.alibaba.nacos.common.utils.UuidUtils;
import com.soukon.novelEditorAi.agent.AgentState;
import com.soukon.novelEditorAi.agent.WritingAgent;
import com.soukon.novelEditorAi.agent.EnhancedWritingAgent;
import com.soukon.novelEditorAi.agent.RagEnhancedWritingAgent;
import com.soukon.novelEditorAi.agent.tool.WritingToolManager;
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

    @Getter
    private ConcurrentHashMap<String, PlanContext> planContextMap = new ConcurrentHashMap<>();

    @Autowired
    private ItemService itemService;

    @Autowired
    private WritingToolManager writingToolManager;

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
        log.info("[RAG增强写作] 开始分析章节内容生成需求");

        // 验证请求参数
        validateRequest(request);

        // 查询并构建章节上下文
        ChapterContext context = buildChapterContext(request.getChapterId());
        request.setChapterContext(context);

        // 设置默认参数
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(defaultMaxTokens);
        } else if (request.getMaxTokens() > 4000) {
            log.warn("[RAG增强写作] 请求的 token 数过大，已限制为 4000");
            request.setMaxTokens(4000);
        }
        if (request.getTemperature() == null) {
            request.setTemperature(defaultTemperature);
        }

        // 生成计划ID
        String planId = UuidUtils.generateUuid();
        PlanContext planContext = new PlanContext(planId);
        request.setPlanContext(planContext);

        log.info("[RAG增强写作] 计划ID: {}", planId);

        try {
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

            // 第二阶段：Planning - 制定写作计划
            log.info("[RAG增强写作] 开始制定写作计划");
            
            BeanOutputConverter<PlanRes> planConverter = new BeanOutputConverter<>(PlanRes.class);
            Prompt planPrompt = new Prompt(reasoningMessages, 
                    OpenAiChatOptions.builder()
                            .temperature(request.getTemperature().doubleValue())
                            .maxTokens(request.getMaxTokens())
                            .build());

            String planResponse = llmService.getAgentChatClient(planId).getChatClient()
                    .prompt(planPrompt).call().content();
            log.info("[RAG增强写作] 计划生成响应: {}", planResponse);

            PlanRes planRes = planConverter.convert(planResponse);
            if (planRes == null || planRes.getPlanList() == null || planRes.getPlanList().isEmpty()) {
                log.error("[RAG增强写作] 计划生成失败或为空");
                planContext.setPlanState(PlanState.COMPLETED);
                planContext.setMessage("计划生成失败");
                return;
            }

            log.info("[RAG增强写作] 计划制定完成，共 {} 个步骤", planRes.getPlanList().size());

            // 第三阶段：Execution - 使用RAG增强写作代理执行写作
            log.info("[RAG增强写作] 开始执行写作计划");
            
            // 创建RAG增强写作代理
            RagEnhancedWritingAgent ragAgent = new RagEnhancedWritingAgent(
                llmService, 
                writingToolManager, 
                chatClient, 
                request
            );
            
            // 设置计划ID
            ragAgent.setPlanId(planId);

            // 执行写作流程
            Flux<String> contentStream = ragAgent.executeWritingPlan(
                request, 
                reasoningMessages, 
                planContext, 
                planRes.getPlanList()
            );

            // 设置流式响应并立即订阅以触发执行
            planContext.setPlanStream(contentStream);
            planContext.setPlanState(PlanState.GENERATING);
            
            // 立即订阅流以触发执行（冷流需要订阅才会执行）
            contentStream.subscribe(
                content -> {
                    log.debug("[RAG增强写作] 生成内容片段: {}", content.length() > 50 ? content.substring(0, 50) + "..." : content);
                },
                error -> {
                    log.error("[RAG增强写作] 内容生成失败", error);
                    planContext.setPlanState(PlanState.COMPLETED);
                    planContext.setMessage("生成失败: " + error.getMessage());
                },
                () -> {
                    log.info("[RAG增强写作] 内容生成完成");
                    planContext.setPlanState(PlanState.COMPLETED);
                    planContext.setMessage("生成完成");
                }
            );

            log.info("[RAG增强写作] 写作流程启动完成");

        } catch (Exception e) {
            log.error("[RAG增强写作] 执行失败", e);
            planContext.setPlanState(PlanState.COMPLETED);
            planContext.setMessage("执行失败: " + e.getMessage());
        }
    }
} 