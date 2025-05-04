package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.entities.CharacterRelationship;
import com.soukon.novelEditorAi.mapper.ChapterMapper;
import com.soukon.novelEditorAi.mapper.CharacterMapper;
import com.soukon.novelEditorAi.mapper.PlotMapper;
import com.soukon.novelEditorAi.mapper.ProjectMapper;
import com.soukon.novelEditorAi.mapper.WorldMapper;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.ChapterContentResponse;
import com.soukon.novelEditorAi.model.chapter.ChapterContext;
import com.soukon.novelEditorAi.service.ChapterContentService;
import com.soukon.novelEditorAi.service.RagService;
import com.soukon.novelEditorAi.service.ProjectService;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.service.WorldService;
import com.soukon.novelEditorAi.service.CharacterService;
import com.soukon.novelEditorAi.service.PlotService;
import com.soukon.novelEditorAi.service.CharacterRelationshipService;
import com.soukon.novelEditorAi.service.OutlinePlotPointService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

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
    private final ProjectService projectService;
    private final ChapterService chapterService;
    private final WorldService worldService;
    private final CharacterService characterService;
    private final PlotService plotService;
    private final CharacterRelationshipService characterRelationshipService;
    private final OutlinePlotPointService outlinePlotPointService;

    @Value("${novel.chapter.default-max-tokens:2000}")
    private Integer defaultMaxTokens;


    @Value("${novel.chapter.default-words-count:5000}")
    private Integer defaultWordsCount;

    @Value("${novel.chapter.default-temperature:0.7}")
    private Float defaultTemperature;

    @Value("${novel.rag.max-results:5}")
    private Integer ragMaxResults;

    @Value("${novel.rag.enabled:true}")
    private Boolean ragEnabled;
    private static final String CHAPTER_CONTENT_PROMPT = """
            你是一位专业的小说写作专家，擅长根据提供的上下文和要求编写小说章节内容。
            
            请根据提供的章节上下文信息，按照要求生成符合以下标准的章节内容：
            1. 内容必须符合小说风格、主题和设定
            2. 情节需要推动故事发展，不能平淡无起伏
            3. 角色性格和行为应保持连贯一致
            4. 章节应包含适当的描写、对话和内心活动
            5. 文风要符合指定的写作偏好
            6. 确保内容衔接自然，与前面章节保持连贯
            7. 如果提供了现有内容，需要自然地衔接和扩展
            
            请不要添加章节标题或数字编号，直接从正文内容开始编写。
            根据指定的写作偏好和风格，创作符合要求的章节内容。
            """;


    @Autowired
    public ChapterContentServiceImpl(ChatModel openAiChatModel,
                                     ProjectMapper projectMapper,
                                     ChapterMapper chapterMapper,
                                     WorldMapper worldMapper,
                                     CharacterMapper characterMapper,
                                     PlotMapper plotMapper,
                                     RagService ragService,
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

        // 构建推理指导提示词
        List<Message> reasoningMessages = buildReasoningPrompt(request);

        // 执行推理过程，分析章节要求并制定写作计划
        String reasoningResult;
        try {
            log.info("[Reasoning] 正在分析章节要求并制定写作计划");
            reasoningResult = chatClient.prompt(new Prompt(reasoningMessages)).call().content();
            log.info("[Reasoning] 完成章节分析和写作计划，长度: {}", reasoningResult != null ? reasoningResult.length() : 0);
        } catch (Exception e) {
            log.error("[Reasoning] 章节分析失败: {}", e.getMessage(), e);
            throw new RuntimeException("分析章节要求失败：" + e.getMessage(), e);
        }

        // 第二阶段：Acting - 根据分析结果执行写作
        log.info("[Acting] 开始根据分析结果生成章节内容");

        // 构建执行提示词，将推理结果融入提示中
        List<Message> actingMessages = buildActingPrompt(request, reasoningResult);

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
            reasoningMessages.clear();
            actingMessages.clear();
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
     * 构建推理阶段的提示词
     * 用于分析章节要求并制定写作计划
     */
    private List<Message> buildReasoningPrompt(ChapterContentRequest request) {
        ChapterContext context = request.getChapterContext();
        if (context == null) {
            throw new IllegalStateException("章节上下文未构建");
        }

        // 系统提示词 - 引导AI进行推理分析
        String systemPrompt = """
                你是一位专业的小说分析和规划专家，擅长分析写作需求并制定写作计划。
              
                你将根据以下上下文。制定一个详细的章节写作计划：
                1. 小说元数据，包括小说标题、类型、结构类型、小说风格、目标字数、亮点、写作要求、世界观、主要角色、角色关系、小说整体大纲等信息
                2. 写作目标：需要创作或续写的字数、写作建议、需要创作或续写的章节
                3. 当前的写作进度和需要创作或续写的章节信息，包括章节的标题、类型、目标字数、摘要、背景、该章节位于全篇的第几章，该章节的目标字数、该章节已有内容，上章节概要
                
                计划要求：
                1. 仔细分析小说背景、角色、世界观和已有情节
                2. 识别关键的情节要素、冲突点和情感线索
                3. 确定本章节在整体故事中的作用和目标
                4. 设计合理的章节结构，包括起承转合
                5. 制定角色对话、情节推进和环境描写的计划
                6. 分析上下文中提到的特定写作风格和偏好
                
               
                请先思考，然后输出一个结构化的章节写作计划，包括：
                - 内容一定要和当前的情节相吻合，不得写出超出当前情节规划的事情
                - 根据章节字数和目标输出字数，合理规划当前输出内容
                - 核心主题和目标
                - 情节架构和节奏
                - 关键场景设计
                - 角色互动计划
                - 冲突和转折点设计
                
                你的分析和计划将作为下一步实际写作的指导。
                """;

        // 用户提示词 - 包含章节上下文信息
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("请根据以下上下文信息，分析并制定本章节的写作计划：\n\n");

        // 添加各种上下文信息
        // 项目信息
        if (context.getProject() != null) {
            userPromptBuilder.append(projectService.toPrompt(context.getProject()));
            userPromptBuilder.append("\n"); // 添加空行分隔
        }

        // 章节信息
        String previousChapterSummary = context.getPreviousChapter() != null ? context.getPreviousChapter().getSummary() : null;
        if (context.getCurrentChapter() != null) {
            userPromptBuilder.append(chapterService.toPrompt(context.getCurrentChapter(), previousChapterSummary));
            userPromptBuilder.append("\n"); // 添加空行分隔
        }

        // 世界观信息
        if (context.getWorld() != null) {
            userPromptBuilder.append(worldService.toPrompt(context.getWorld()));
            userPromptBuilder.append("\n"); // 添加空行分隔
        }

        // 角色信息
        if (context.getCharacters() != null && !context.getCharacters().isEmpty()) {
            userPromptBuilder.append("主要角色:\n");
            context.getCharacters().forEach(character -> userPromptBuilder.append(characterService.toPrompt(character)));
            userPromptBuilder.append("\n"); // 添加空行分隔
        }

        // 角色关系信息
        if (context.getCharacterRelationships() != null && !context.getCharacterRelationships().isEmpty()) {
            userPromptBuilder.append("角色关系:\n");
            context.getCharacterRelationships().forEach(rel -> userPromptBuilder.append(characterRelationshipService.toPrompt(rel)));
            userPromptBuilder.append("\n");
        }

        // 本章情节
        if (context.getChapterPlots() != null && !context.getChapterPlots().isEmpty()) {
            userPromptBuilder.append("本章节需要包含的情节:\n");
            context.getChapterPlots().forEach(plot -> userPromptBuilder.append(plotService.toPrompt(plot)));
            userPromptBuilder.append("\n"); // 添加空行分隔
        }

        // 大纲情节点信息
        if (context.getPlotPoints() != null && !context.getPlotPoints().isEmpty()) {
            userPromptBuilder.append("大纲情节点:\n");
            context.getPlotPoints().forEach(point -> userPromptBuilder.append(outlinePlotPointService.toPrompt(point)));
            userPromptBuilder.append("\n");
        }

        // 相关背景信息 (RAG)
        String relevantInfo = retrieveRelevantInfo(request.getChapterId());
        if (relevantInfo != null && !relevantInfo.isEmpty()) {
            userPromptBuilder.append("相关背景信息:\n").append(relevantInfo).append("\n\n");
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
     *
     * @param request         章节内容请求
     * @param reasoningResult 推理阶段生成的分析和计划
     * @return 消息列表
     */
    private List<Message> buildActingPrompt(ChapterContentRequest request, String reasoningResult) {
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
     *
     * @param chapterId 章节ID
     * @return 相关信息文本
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

        // 构建推理指导提示词
        List<Message> reasoningMessages = buildReasoningPrompt(request);

        // 执行推理过程，分析章节要求并制定写作计划
        String reasoningResult;
        try {
            log.info("[Reasoning] 正在分析章节要求并制定写作计划");
            reasoningResult = chatClient.prompt(new Prompt(reasoningMessages)).call().content();
            log.info("[Reasoning] 完成章节分析和写作计划，长度: {}", reasoningResult != null ? reasoningResult.length() : 0);
        } catch (Exception e) {
            log.error("[Reasoning] 章节分析失败: {}", e.getMessage(), e);
            throw new RuntimeException("分析章节要求失败：" + e.getMessage(), e);
        }

        // 第二阶段：Acting - 根据分析结果执行写作
        log.info("[Acting] 开始根据分析结果生成章节内容");

        // 构建执行提示词，将推理结果融入提示中
        List<Message> actingMessages = buildActingPrompt(request, reasoningResult);

        // 调用AI模型生成实际章节内容
        String generatedContent;
        log.info("[Acting] 正在生成章节内容");
        Flux<String> contentFlux = chatClient.prompt(new Prompt(actingMessages)).stream().content();

        // 手动触发GC回收不再使用的大对象
        reasoningMessages.clear();
        actingMessages.clear();
        System.gc();

        // 计算生成耗时
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("[Acting] 总耗时: {}ms", duration);

//            .doOnNext(contentBuilder::append)
//            .doOnComplete(() -> {
//                // 流式内容全部生成后自动保存（覆盖模式）
//                saveChapterContent(request.getChapterId(), contentBuilder.toString(), false);
//            })
        ;
        return contentFlux;
    }
} 