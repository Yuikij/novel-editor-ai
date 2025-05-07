package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.mapper.ChapterMapper;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.service.ProjectService;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChapterServiceImpl extends ServiceImpl<ChapterMapper, Chapter> implements ChapterService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed


    private final ChapterMapper chapterMapper;


    private final ProjectService projectService;

    private final ChatClient chatClient;

    @Autowired
    public ChapterServiceImpl(ChapterMapper chapterMapper, ProjectService projectService,ChatModel openAiChatModel) {
        this.chapterMapper = chapterMapper;
        this.projectService = projectService;
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .temperature(0.7)
                                .build()
                )
                .build();
    }

    /**
     * 生成用于构建生成请求 Prompt 的章节信息部分。
     *
     * @param chapter                章节实体
     * @param previousChapterSummary 上一章节的摘要（如果存在）。
     * @return 包含章节标题、摘要、背景和上一章节摘要的字符串。
     */
    @Override
    public String toPrompt(Chapter chapter, String previousChapterSummary) {
        if (chapter == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) {
            sb.append("章节标题: ").append(chapter.getTitle()).append("\n");
        }
        if (chapter.getType() != null && !chapter.getType().isEmpty()) {
            sb.append("章节类型: ").append(chapter.getType()).append("\n");
        }

        if (chapter.getWordCountGoal() != null) {
            sb.append("章节目标字数: ").append(chapter.getWordCountGoal()).append("\n");
        }
        if (chapter.getSummary() != null && !chapter.getSummary().isEmpty()) {
            sb.append("章节摘要: ").append(chapter.getSummary()).append("\n");
        }
        if (chapter.getNotes() != null && !chapter.getNotes().isEmpty()) { // 使用 notes 作为章节背景
            sb.append("章节背景: ").append(chapter.getNotes()).append("\n");
        }

        if (previousChapterSummary != null && !previousChapterSummary.isEmpty()) {
            sb.append("上一章节摘要: ").append(previousChapterSummary).append("\n");
        }

        // 添加章节位置信息
        Long projectId = chapter.getProjectId();
        Integer chapterPosition = chapter.getSortOrder();
        if (projectId != null && chapterPosition != null) {
            // 查询项目总章节数
            LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Chapter::getProjectId, projectId);
            long totalChaptersLong = count(queryWrapper);
            int totalChapters = (int) totalChaptersLong;

            sb.append("章节位置: 第").append(chapterPosition).append("章 (共").append(totalChapters).append("章)\n");
        }

        return sb.toString();
    }

    /**
     * 生成用于构建生成请求 Prompt 的章节信息部分，自动查询上一章节的摘要。
     *
     * @param chapter 章节实体
     * @return 包含章节标题、摘要、背景和上一章节摘要的字符串。
     */
    @Override
    public String toPrompt(Chapter chapter) {
        if (chapter == null) {
            return "";
        }

        // 自动查询上一章节摘要
        String previousChapterSummary = null;
        Long projectId = chapter.getProjectId();
        Integer sortOrder = chapter.getSortOrder();

        if (projectId != null && sortOrder != null && sortOrder > 1) {
            // 查询上一章节
            Chapter previousChapter = chapterMapper.selectByProjectIdAndOrder(projectId, sortOrder - 1);
            if (previousChapter != null) {
                previousChapterSummary = previousChapter.getSummary();
            }
        }

        // 调用现有方法生成提示内容
        return toPrompt(chapter, previousChapterSummary);
    }

    @Override
    public String toPromptProjectId(Long projectId) {
        LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Chapter::getProjectId, projectId);
        queryWrapper.orderByAsc(Chapter::getSortOrder);
        List<Chapter> chapters = list(queryWrapper);
        StringBuilder chaptersInfo = new StringBuilder();
        if (chapters != null && !chapters.isEmpty()) {
            chaptersInfo.append("章节列表 (").append(chapters.size()).append("章):\n");
            for (Chapter chapter : chapters) {
                // 使用直接查库的toPrompt方法，自动获取上一章节摘要
                chaptersInfo.append(toPrompt(chapter));
                chaptersInfo.append("-----\n");
            }
        }
        return chaptersInfo.toString();
    }

    @Override
    public String toPromptChapterId(Long chapterId) {
        StringBuilder chapterInfo = new StringBuilder("章节信息：\n");
        Chapter chapter = getById(chapterId);
        String prompt = toPrompt(chapter);
        chapterInfo.append(prompt);
        return chapterInfo.toString();
    }

    /**
     * 根据已有的章节，补全或扩展章节列表到目标数量
     *
     * @param projectId          项目ID
     * @param existingChapterIds 已有的章节ID列表
     * @param targetCount        目标章节总数
     * @return 补全后的章节列表（包含已有的和新生成的）
     */
    @Override
    public List<Chapter> expandChapters(Long projectId, List<Long> existingChapterIds, Integer targetCount) {
        // 如果未指定目标数量，设置默认值为12
        if (targetCount == null || targetCount < 1) {
            targetCount = 12;
        }

        // 获取已有的章节
        List<Chapter> existingChapters = new ArrayList<>();
        if (existingChapterIds != null && !existingChapterIds.isEmpty()) {
            existingChapters = this.listByIds(existingChapterIds);
            // 按照sortOrder排序，确保序列正确
            existingChapters.sort(Comparator.comparing(Chapter::getSortOrder));
        }

        // 如果已经达到或超过目标数量，直接返回
        if (existingChapters.size() >= targetCount) {
            return existingChapters;
        }

        // 调用LLM补全章节
        List<Chapter> newChapters = callLlmForChapterExpansion(projectId, existingChapters, targetCount);

        // 设置基本属性并保存到数据库
        int sortOrder = existingChapters.isEmpty() ? 1 :
                existingChapters.stream().mapToInt(c -> c.getSortOrder()).max().orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();

        for (Chapter chapter : newChapters) {
            chapter.setProjectId(projectId);
            chapter.setSortOrder(sortOrder++);
            chapter.setCreatedAt(now);
            chapter.setUpdatedAt(now);
            chapter.setStatus("draft");
            chapter.setWordCount(0L);
            // 保存到数据库
            this.save(chapter);
        }

        // 合并已有的和新生成的章节，并按sortOrder排序
        List<Chapter> allChapters = new ArrayList<>(existingChapters);
        allChapters.addAll(newChapters);
        allChapters.sort(Comparator.comparing(Chapter::getSortOrder));

        return allChapters;
    }

    /**
     * 调用LLM补全或扩展章节列表
     *
     * @param projectId        项目ID
     * @param existingChapters 已有的章节列表
     * @param targetCount      目标章节总数
     * @return 生成的新章节列表
     */
    private List<Chapter> callLlmForChapterExpansion(Long projectId, List<Chapter> existingChapters, Integer targetCount) {
        // 构建小说上下文信息
        Map<String, Object> context = buildNovelContext(projectId);

        // 系统提示词 - 引导AI扩展章节
        String systemPrompt = """
                你是一个专业的小说章节规划助手，帮助作者规划章节结构。
                请根据已有的章节列表和小说基本信息，补充生成新的章节规划，使总章节数达到目标数量。
                                
                每个章节应包含以下信息：
                1. 章节标题：简洁且能反映章节内容的标题
                2. 章节摘要：概述本章节的主要内容和情节发展
                3. 章节备注：可以包含写作提示、情节建议或重要的场景描述
                                
                请确保新生成的章节与已有章节在情节和风格上保持连贯性，同时推动故事情节向前发展。
                请严格按照JSON格式返回结果，每个章节对象包含title、summary和notes三个字段。
                                
                格式示例：
                [
                  {
                    "title": "章节标题",
                    "summary": "章节摘要内容",
                    "notes": "章节备注或写作建议"
                  }
                ]
                """;

        // 用户提示词 - 构建上下文和请求
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("## 小说基本信息\n");

        // 添加项目相关信息
        context.forEach((key, value) -> {
            if (value != null && !value.toString().isEmpty()) {
                userPromptBuilder.append(key).append(": ").append(value).append("\n");
            }
        });

        // 添加已有章节
        userPromptBuilder.append("\n## 已有章节\n");
        if (existingChapters.isEmpty()) {
            userPromptBuilder.append("当前没有已有章节，请创建全新的章节规划。\n");
        } else {
            for (int i = 0; i < existingChapters.size(); i++) {
                Chapter chapter = existingChapters.get(i);
                userPromptBuilder.append(i + 1).append(". ");
                userPromptBuilder.append("【").append(chapter.getTitle()).append("】");
                userPromptBuilder.append(" 摘要: ").append(chapter.getSummary() != null ? chapter.getSummary() : "无摘要");
                userPromptBuilder.append(" 备注: ").append(chapter.getNotes() != null ? chapter.getNotes() : "无备注").append("\n");
            }
        }

        userPromptBuilder.append("\n请创建约").append(targetCount - existingChapters.size())
                .append("个新章节，使总数达到").append(targetCount).append("个左右。");

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));
        log.info("AI扩展章节请求: {}", messages);

        try {
            // 发送请求到AI服务
            Prompt prompt = new Prompt(messages);
            String response = chatClient.prompt(prompt).call().content();
            log.info("AI扩展章节响应: {}", response);

            // 解析JSON响应
            return parseChaptersFromJson(response);
        } catch (Exception e) {
            log.error("调用AI扩展章节失败: {}", e.getMessage(), e);
            // 失败时返回空列表
            return Collections.emptyList();
        }
    }

    /**
     * 从JSON字符串解析章节列表
     *
     * @param json JSON格式的章节列表字符串
     * @return 解析后的章节对象列表
     */
    private List<Chapter> parseChaptersFromJson(String json) {
        try {
            // 提取JSON部分
            String jsonContent = extractJsonFromString(json);

            // 解析JSON到章节对象列表
            return new ObjectMapper().readValue(jsonContent,
                    new TypeReference<List<Chapter>>() {
                    });
        } catch (Exception e) {
            log.error("解析章节JSON失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 从字符串中提取JSON部分
     */
    private String extractJsonFromString(String input) {
        // 尝试找出JSON数组的起始和结束位置
        int startIdx = input.indexOf('[');
        int endIdx = input.lastIndexOf(']') + 1;

        if (startIdx >= 0 && endIdx > startIdx) {
            return input.substring(startIdx, endIdx);
        }

        // 如果找不到JSON数组标记，返回原始输入
        return input;
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
            if (project != null) {
                context.put("小说标题", project.getTitle());
                context.put("小说简介", project.getSynopsis());
                context.put("小说风格", project.getStyle());
                // 注释掉暂不支持的字段
                // context.put("写作目标", project.getGoal());
            }

            // 可以添加更多项目相关信息，如世界观、角色等

        } catch (Exception e) {
            log.error("构建小说上下文失败: {}", e.getMessage(), e);
        }

        return context;
    }
} 