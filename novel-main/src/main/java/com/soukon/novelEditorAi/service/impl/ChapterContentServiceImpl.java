package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.entities.World;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Value("${novel.chapter.default-max-tokens:2000}")
    private Integer defaultMaxTokens;

    @Value("${novel.chapter.default-temperature:0.7}")
    private Float defaultTemperature;
    
    @Value("${novel.rag.max-results:5}")
    private Integer ragMaxResults;
    
    @Value("${novel.rag.enabled:true}")
    private Boolean ragEnabled;

    private static final String CHAPTER_CONTENT_PROMPT = """
            你是一位专业的小说创作AI助手，擅长根据提供的上下文和要求编写小说章节内容。
            
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
                                     RagService ragService) {
        this.chatClient = ChatClient.builder(openAiChatModel)
                // 实现 Chat Memory 的 Advisor
                // 在使用 Chat Memory 时，需要指定对话 ID，以便 Spring AI 处理上下文。
//                .defaultAdvisors(
//                        new MessageChatMemoryAdvisor(new InMemoryChatMemory())
//                )
                // 实现 Logger 的 Advisor
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .topP(0.7)
                                .build()
                )
                .build();;
        this.projectMapper = projectMapper;
        this.chapterMapper = chapterMapper;
        this.worldMapper = worldMapper;
        this.characterMapper = characterMapper;
        this.plotMapper = plotMapper;
        this.ragService = ragService;
    }

    @Override
    public ChapterContentResponse generateChapterContent(ChapterContentRequest request) {
        long startTime = System.currentTimeMillis();
        
        // 验证请求参数
        validateRequest(request);

        // 查询并构建章节上下文
        ChapterContext context = buildChapterContext(request.getChapterId());
        request.setChapterContext(context);
        
        // 设置默认参数
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(defaultMaxTokens);
        }
        if (request.getTemperature() == null) {
            request.setTemperature(defaultTemperature);
        }
        
        // 构建提示词
        List<Message> messages = buildPrompt(request);
        
        // 调用AI模型生成内容
        String generatedContent = chatClient.prompt(new Prompt(messages)).call().content();
        
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
        
        // 如果启用了RAG，索引生成的内容
        if (ragEnabled && generatedContent != null && !generatedContent.isEmpty()) {
            try {
                ragService.indexChapter(request.getChapterId());
            } catch (Exception e) {
                log.warn("索引章节内容失败: {}", e.getMessage());
            }
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
    public boolean saveChapterContent(Long chapterId, String content) {
        try {
            Chapter chapter = chapterMapper.selectById(chapterId);
            if (chapter != null) {
                chapter.setContent(content);
                chapterMapper.updateById(chapter);
                
                // 更新索引
                if (ragEnabled) {
                    try {
                        ragService.indexChapter(chapterId);
                    } catch (Exception e) {
                        log.warn("索引章节内容失败: {}", e.getMessage());
                    }
                }
                
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("保存章节内容失败: {}", e.getMessage());
            return false;
        }
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
     * 构建生成提示词
     */
    private List<Message> buildPrompt(ChapterContentRequest request) {
        List<Message> messages = new ArrayList<>();
        
        // 系统提示词
        messages.add(new SystemMessage(CHAPTER_CONTENT_PROMPT));
        
        // 构建上下文提示词
        Map<String, Object> contextMap = new HashMap<>();
        ChapterContext context = request.getChapterContext();
        
        contextMap.put("novelTitle", context.getNovelTitle());
        contextMap.put("novelSummary", context.getNovelSummary());
        contextMap.put("novelStyle", context.getNovelStyle());
        contextMap.put("chapterTitle", request.getChapterTitle() != null ? 
                request.getChapterTitle() : context.getCurrentChapter().getTitle());
        contextMap.put("chapterSummary", context.getChapterSummary());
        contextMap.put("previousChapterSummary", context.getPreviousChapterSummary());
        contextMap.put("chapterBackground", context.getChapterBackground());
        
        if (context.getWorld() != null) {
            contextMap.put("worldName", context.getWorld().getName());
            contextMap.put("worldDescription", context.getWorld().getDescription());
        }
        
        if (context.getCharacters() != null && !context.getCharacters().isEmpty()) {
            StringBuilder charactersInfo = new StringBuilder();
            context.getCharacters().forEach(character -> {
                charactersInfo.append("- ").append(character.getName())
                        .append(": ").append(character.getDescription())
                        .append("\n");
            });
            contextMap.put("characters", charactersInfo.toString());
        }
        
        if (context.getChapterPlots() != null && !context.getChapterPlots().isEmpty()) {
            StringBuilder plotsInfo = new StringBuilder();
            context.getChapterPlots().forEach(plot -> {
                plotsInfo.append("- ").append(plot.getDescription())
                        .append("\n");
            });
            contextMap.put("plots", plotsInfo.toString());
        }
        
        // 如果启用了RAG，添加相关文档
        if (ragEnabled) {
            String relevantInfo = retrieveRelevantInfo(request.getChapterId());
            if (relevantInfo != null && !relevantInfo.isEmpty()) {
                contextMap.put("relevantInfo", relevantInfo);
            }
        }
        
        String contextPrompt = """
                请根据以下上下文信息，创作符合要求的章节内容：
                
                小说标题: {{novelTitle}}
                小说概要: {{novelSummary}}
                小说风格: {{novelStyle}}
                章节标题: {{chapterTitle}}
                章节摘要: {{chapterSummary}}
                {%if previousChapterSummary != null %}上一章节摘要: {{previousChapterSummary}}{%endif%}
                {%if chapterBackground != null %}章节背景: {{chapterBackground}}{%endif%}
                
                {%if worldName != null %}
                世界观:
                {{worldName}}: {{worldDescription}}
                {%endif%}
                
                {%if characters != null %}
                主要角色:
                {{characters}}
                {%endif%}
                
                {%if plots != null %}
                本章节需要包含的情节:
                {{plots}}
                {%endif%}
                
                {%if relevantInfo != null %}
                相关背景信息:
                {{relevantInfo}}
                {%endif%}
                
                {%if existingContent != null %}
                已有内容（需要续写）:
                {{existingContent}}
                {%endif%}
                
                请根据上述信息，{%if existingContent != null %}续写{%else%}创作{%endif%}本章节内容。
                """;
                
        // 添加已有内容（如果有）
        if (request.getExistingContent() != null && !request.getExistingContent().isEmpty()) {
            contextMap.put("existingContent", request.getExistingContent());
        }
        
        // 生成上下文提示词
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(contextPrompt);
        Message contextMessage = promptTemplate.createMessage(contextMap);
        String formattedPrompt = contextMessage.getText();
        messages.add(new UserMessage(formattedPrompt));
        
        // 添加自定义提示词（如果有）
        if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            messages.add(new UserMessage(request.getPrompt()));
        }
        
        return messages;
    }
    
    /**
     * 检索与章节相关的信息
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
            log.warn("检索相关信息失败: {}", e.getMessage());
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
} 