package com.soukon.novelEditorAi.service.impl;

import com.alibaba.nacos.common.utils.UuidUtils;
import com.soukon.novelEditorAi.agent.WritingAgent;
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
import com.soukon.novelEditorAi.service.ChapterContentService;
import com.soukon.novelEditorAi.service.RagService;
import com.soukon.novelEditorAi.service.ProjectService;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.service.WorldService;
import com.soukon.novelEditorAi.service.CharacterService;
import com.soukon.novelEditorAi.service.PlotService;
import com.soukon.novelEditorAi.service.CharacterRelationshipService;
import com.soukon.novelEditorAi.service.OutlinePlotPointService;
import com.soukon.novelEditorAi.service.PromptService;
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
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.soukon.novelEditorAi.model.chapter.ReasoningRes;
import org.springframework.ai.converter.BeanOutputConverter;

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
    public ChapterContentResponse generateChapterContent(ChapterContentRequest request) {
        long startTime = System.currentTimeMillis();

        // 第一阶段：Reasoning - 理解和分析需求
        log.info("[Reasoning] 开始分析章节内容生成需求");

        // 验证请求参数
        validateRequest(request);

        // 查询并构建章节上下文
        ChapterContext context = buildChapterContext(request.getChapterId());
        request.setChapterContext(context);

        // 设置默认参数
        if (request.getMaxTokens() == null) {
            // 设置一个合理的默认 token 上限，防止生成过长内容
            request.setMaxTokens(defaultMaxTokens);
        } else if (request.getMaxTokens() > 4000) {
            // 限制最大 token 数，即使用户请求更多也进行控制
            log.warn("[Reasoning] 请求的 token 数过大，已限制为 4000");
            request.setMaxTokens(4000);
        }
        if (request.getTemperature() == null) {
            request.setTemperature(defaultTemperature);
        }

        // 构建推理指导提示词 - 使用PromptService
        List<Message> reasoningMessages = promptService.buildReasoningPrompt(request);

        // 执行推理过程，分析章节要求并制定写作计划
        String reasoningResult;
        ReasoningRes reasoningRes;
        PlanRes planRes;
        try {
            BeanOutputConverter<PlanRes> converter = new BeanOutputConverter<>(PlanRes.class);
            log.info("[Reasoning] 正在分析章节要求并制定写作计划");
            reasoningResult = llmService.getAgentChatClient(request.getChapterId() + "").getChatClient().prompt(new Prompt(reasoningMessages)).call().content();
//            reasoningResult = chatClient.prompt(new Prompt(reasoningMessages)).call().content();
            planRes = reasoningResult == null ? null : converter.convert(reasoningResult);
            log.info("[Reasoning] 完成章节分析和写作计划: {}", planRes);
        } catch (Exception e) {
            log.error("[Reasoning] 章节分析失败: {}", e.getMessage(), e);
            throw new RuntimeException("分析章节要求失败：" + e.getMessage(), e);
        }

        // 将reasoningRes保存到数据库
//        if (reasoningRes != null && reasoningRes.getPlotList() != null && !reasoningRes.getPlotList().isEmpty()) {
//            log.info("[Reasoning] 保存章节情节数据，情节数量: {}", reasoningRes.getPlotList().size());
//
//            // 获取章节ID
//            Long chapterId = request.getChapterId();
//
//            // 获取项目ID
//            Long projectId = context.getProjectId();
//
//            // 先删除原有的章节情节数据，避免重复
//            plotMapper.delete(
//                    Wrappers.<Plot>lambdaQuery()
//                            .eq(Plot::getChapterId, chapterId)
//            );
//
//            // 遍历情节列表并保存
//            for (int i = 0; i < reasoningRes.getPlotList().size(); i++) {
//                PlotRes plotRes = reasoningRes.getPlotList().get(i);
//
//                // 创建新的Plot实体
//                Plot plot = new Plot();
//                plot.setProjectId(projectId);
//                plot.setChapterId(chapterId);
//                plot.setTitle(plotRes.getTitle());
//                plot.setDescription(plotRes.getDescription());
//                plot.setSortOrder(plotRes.getSortOrder() != null ? plotRes.getSortOrder() : i + 1);
//                plot.setStatus(plotRes.getStatus());
//                plot.setCompletionPercentage(plotRes.getCompletionPercentage());
//                plot.setWordCountGoal(plotRes.getWordCountGoal());
//
//                // 设置创建和更新时间
//                LocalDateTime now = LocalDateTime.now();
//                plot.setCreatedAt(now);
//                plot.setUpdatedAt(now);
//
//                // 保存情节
//                plotMapper.insert(plot);
//            }
//
//            log.info("[Reasoning] 成功保存章节情节数据");
//        } else {
//            log.warn("[Reasoning] 章节情节数据为空，不保存");
//        }

        // 第二阶段：Acting - 根据分析结果执行写作
        log.info("[Acting] 开始根据分析结果生成章节内容");

        // 构建执行提示词，将推理结果融入提示中 - 使用PromptService
        List<Message> actingMessages = promptService.buildActingPrompt(request, reasoningResult);

        // 调用AI模型生成实际章节内容
        String generatedContent;
        try {
            log.info("[Acting] 正在生成章节内容");
            generatedContent = chatClient.prompt(new Prompt(actingMessages)).call().content();

            // 如果内容过长，截断处理 
            if (generatedContent != null && generatedContent.length() > 100000) {
                log.warn("[Acting] 生成的内容过长，已截断至10万字符");
                generatedContent = generatedContent.substring(0, 100000);
            }

            // 手动触发GC回收不再使用的大对象
            System.gc();

            log.info("[Acting] 完成章节内容生成，字数: {}", generatedContent != null ? generatedContent.length() : 0);
        } catch (Exception e) {
            log.error("[Acting] 生成章节内容失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成章节内容失败：" + e.getMessage(), e);
        }

        // 计算生成耗时
        long endTime = System.currentTimeMillis();

        // 创建响应对象
        ChapterContentResponse response = ChapterContentResponse.builder()
                .chapterId(request.getChapterId())
                .projectId(context.getProjectId())
                .content(generatedContent)
                .wordCount(countWords(generatedContent))
                .generationTime(endTime - startTime)
                .isComplete(true)
                .build();

        // 如果启用了RAG，索引生成的内容，增强错误处理
        if (ragEnabled && generatedContent != null && !generatedContent.isEmpty()) {
            // 使用异步方式处理索引，避免阻塞主流程
            CompletableFuture.runAsync(() -> {
                try {
                    ragService.indexChapter(request.getChapterId());
                } catch (OutOfMemoryError e) {
                    log.error("索引章节内容时发生内存溢出: {}", e.getMessage());
                    // 内存溢出是严重问题，记录但不重试
                    // 可以考虑发送告警或通知管理员
                } catch (Exception e) {
                    log.warn("索引章节内容失败: {}", e.getMessage());
                }
            });
        }

        return response;
    }

    @Override
    public ChapterContentResponse generateChapterContentStream(ChapterContentRequest request) {
        // 实现流式生成的逻辑，具体实现可根据需求调整
        // 由于涉及到异步处理，此处仅提供基本实现
        return generateChapterContent(request);
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
    public boolean saveChapterContent(Long chapterId, String content) {
        // 调用重载的方法，默认为覆盖模式
        return saveChapterContent(chapterId, content, false);
    }

    @Override
    public Result<String> generateChapterContentExecute(ChapterContentRequest request) {
        String planId = UuidUtils.generateUuid();
        
        // 创建新的计划上下文
        PlanContext planContext = new PlanContext(planId);
        planContext.setPlanState(PlanState.PLANNING);
        planContext.setPlanStreams(new HashMap<>());
        planContextMap.put(planId, planContext);
        
        // 异步执行任务
        CompletableFuture.supplyAsync(() -> {
            try {
                planContext.setPlanState(PlanState.IN_PROGRESS);
                
                // 生成内容
                Flux<String> contentFlux = generateChapterContentStreamFlux(request);
                
                // 将内容流存入计划上下文
                planContext.getPlanStreams().put(1, contentFlux);
                
                // 更新计划状态
                planContext.setPlanState(PlanState.COMPLETED);
                
                return contentFlux;
            }
            catch (Exception e) {
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
    public Flux<String> generateChapterContentStreamFlux(ChapterContentRequest request) {

        String planId = UuidUtils.generateUuid();
        planContextMap.put(planId, new PlanContext(planId));
        long startTime = System.currentTimeMillis();

        // 第一阶段：Reasoning - 理解和分析需求
        log.info("[Reasoning] 开始分析章节内容生成需求");

        // 验证请求参数
        validateRequest(request);

        // 查询并构建章节上下文
        ChapterContext context = buildChapterContext(request.getChapterId());
        request.setChapterContext(context);

        // 设置默认参数
        if (request.getMaxTokens() == null) {
            // 设置一个合理的默认 token 上限，防止生成过长内容
            request.setMaxTokens(defaultMaxTokens);
        } else if (request.getMaxTokens() > 4000) {
            // 限制最大 token 数，即使用户请求更多也进行控制
            log.warn("[Reasoning] 请求的 token 数过大，已限制为 4000");
            request.setMaxTokens(4000);
        }
        if (request.getTemperature() == null) {
            request.setTemperature(defaultTemperature);
        }

        // 构建推理指导提示词 - 使用PromptService
        List<Message> reasoningMessages = promptService.buildReasoningPrompt(request);

//        // 执行推理过程，分析章节要求并制定写作计划
        String reasoningResult;
        PlanRes planRes;
        try {
            BeanOutputConverter<PlanRes> converter = new BeanOutputConverter<>(PlanRes.class);
            log.info("[Reasoning] 正在分析章节要求并制定写作计划");

            reasoningResult = llmService.getAgentChatClient(request.getChapterId() + "").getChatClient()
                    .prompt(new Prompt(reasoningMessages)).call().content();
            planRes = reasoningResult == null ? null : converter.convert(reasoningResult);
            log.info("[Reasoning] 完成章节分析和写作计划: {}", planRes);
        } catch (Exception e) {
            log.error("[Reasoning] 章节分析失败: {}", e.getMessage(), e);
            throw new RuntimeException("分析章节要求失败：" + e.getMessage(), e);
        }
        if (planRes == null) {
            log.error("[Reasoning] 章节分析结果为空");
            throw new RuntimeException("分析章节要求失败：结果为空");
        }
        // 将reasoningRes保存到数据库
        // 第二阶段：Acting - 根据分析结果执行写作
        log.info("[Acting] 开始根据分析结果生成章节内容");
        List<String> planList = planRes.getPlanList();
        if (planList == null || planList.isEmpty()) {
            log.error("[Acting] 章节计划列表为空");
            throw new RuntimeException("生成章节内容失败：计划列表为空");
        }
        // 生成章节内容
        WritingAgent writingAgent = new WritingAgent();
        planList.forEach(e -> {
//            writingAgent.run(e, planId, request);
//            run(e, planId, request);
        });
        // 构建执行提示词，将推理结果融入提示中 - 使用PromptService
        List<Message> actingMessages = promptService.buildActingPrompt(request, planRes.toString());

        // 调用AI模型生成实际章节内容
        log.info("[Acting] 正在以流式方式生成章节内容");
        Flux<String> contentFlux = llmService.getAgentChatClient(request.getChapterId() + "").getChatClient()
                .prompt(new Prompt(actingMessages)).stream().content();

        // 计算生成耗时
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("[Acting] 流式内容生成准备阶段总耗时: {}ms", duration);

        return contentFlux;
    }
} 