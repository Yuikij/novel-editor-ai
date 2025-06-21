package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.OutlinePlotPoint;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.mapper.ChapterMapper;
import com.soukon.novelEditorAi.model.chapter.ChapterListDTO;
import com.soukon.novelEditorAi.service.*;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
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
    private final CharacterService characterService;
    private final CharacterRelationshipService characterRelationshipService;
    private final OutlinePlotPointService outlinePlotPointService;
    private final EntitySyncHelper entitySyncHelper;

    @Autowired
    public ChapterServiceImpl(ChapterMapper chapterMapper, ProjectService projectService,@Qualifier("openAiChatModel") ChatModel openAiChatModel,
                              CharacterService characterService,CharacterRelationshipService characterRelationshipService,
                              @Lazy OutlinePlotPointService outlinePlotPointService, EntitySyncHelper entitySyncHelper
    ) {
        this.chapterMapper = chapterMapper;
        this.projectService = projectService;
        this.characterService = characterService;
        this.outlinePlotPointService = outlinePlotPointService;
        this.characterRelationshipService = characterRelationshipService;
        this.entitySyncHelper = entitySyncHelper;
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
            context.put("章节信息", toPromptProjectId(projectId));


        } catch (Exception e) {
            log.error("构建小说上下文失败: {}", e.getMessage(), e);
        }
        
        return context;
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

    @Override
    public Page<ChapterListDTO> pageChapterList(int page, int size, Long projectId, String title, String status) {
        try {
            // 分页参数
            Page<ChapterListDTO> pageParam = new Page<>(page, size);
            
            // 执行分页查询（不包含content和historyContent字段）
            Page<ChapterListDTO> resultPage = chapterMapper.selectPageWithoutContent(pageParam, projectId, title, status);
            
            return resultPage;
        } catch (Exception e) {
            log.error("分页查询章节列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    @Override
    public List<ChapterListDTO> getChapterListByProjectId(Long projectId) {
        try {
            if (projectId == null) {
                throw new IllegalArgumentException("项目ID不能为空");
            }
            
            // 执行查询（不包含content和historyContent字段）
            List<ChapterListDTO> chapters = chapterMapper.selectListByProjectIdWithoutContent(projectId);
            
            return chapters;
        } catch (Exception e) {
            log.error("根据项目ID查询章节列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    @Override
    public List<ChapterListDTO> getAllChapterList() {
        try {
            // 执行查询（不包含content和historyContent字段）
            List<ChapterListDTO> chapters = chapterMapper.selectAllWithoutContent();
            
            return chapters;
        } catch (Exception e) {
            log.error("查询所有章节列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取章节最新内容，支持指定字数截取
     * 如果当前章节没有内容，会向前查找上一章节的内容
     *
     * @param chapterId 章节ID
     * @param wordCount 需要获取的字数（为null时返回全部内容）
     * @return 章节内容字符串，如果没有找到任何内容则返回空字符串
     */
    @Override
    public String getLatestChapterContent(Long chapterId, Integer wordCount) {
        if (chapterId == null) {
            throw new IllegalArgumentException("章节ID不能为空");
        }

        try {
            // 获取当前章节
            Chapter currentChapter = getById(chapterId);
            if (currentChapter == null) {
                throw new IllegalArgumentException("找不到指定的章节: " + chapterId);
            }

            Long projectId = currentChapter.getProjectId();
            Integer currentSortOrder = currentChapter.getSortOrder();
            
            if (projectId == null || currentSortOrder == null) {
                log.warn("章节缺少项目ID或排序信息: chapterId={}", chapterId);
                return "";
            }

            // 从当前章节开始向前查找有内容的章节
            for (int sortOrder = currentSortOrder; sortOrder >= 1; sortOrder--) {
                Chapter chapter = chapterMapper.selectByProjectIdAndOrder(projectId, sortOrder);
                if (chapter != null && chapter.getContent() != null && !chapter.getContent().trim().isEmpty()) {
                    String content = chapter.getContent();
                    
                    // 如果指定了字数限制，截取相应长度的内容
                    if (wordCount != null && wordCount > 0 && content.length() > wordCount) {
                        content = content.substring(Math.max(0, content.length() - wordCount));
                    }
                    
                    log.info("找到章节内容: 项目ID={}, 章节排序={}, 内容长度={}", 
                             projectId, sortOrder, content.length());
                    return content;
                }
            }

            // 如果没有找到任何有内容的章节
            log.info("项目ID={}中从第{}章开始向前都没有找到有效内容", projectId, currentSortOrder);
            return "";
            
        } catch (Exception e) {
            log.error("获取章节最新内容失败: chapterId={}, wordCount={}, error={}", 
                     chapterId, wordCount, e.getMessage(), e);
            throw new RuntimeException("获取章节内容失败: " + e.getMessage());
        }
    }

    /**
     * 验证并处理章节的sortOrder，确保在同一项目中不重复
     * 如果发生重复，会自动调整后续章节的sortOrder
     *
     * @param chapter 要保存或更新的章节
     * @param isUpdate 是否为更新操作（true为更新，false为新增）
     * @throws IllegalArgumentException 如果参数无效
     */
    @Override
    public void validateAndHandleSortOrder(Chapter chapter, boolean isUpdate) {
        if (chapter == null) {
            throw new IllegalArgumentException("章节信息不能为空");
        }

        Long projectId = chapter.getProjectId();
        Integer sortOrder = chapter.getSortOrder();

        if (projectId == null) {
            throw new IllegalArgumentException("项目ID不能为空");
        }

        // 如果sortOrder为空，自动设置为最后一章的下一个序号
        if (sortOrder == null) {
            sortOrder = getNextAvailableSortOrder(projectId);
            chapter.setSortOrder(sortOrder);
            log.info("自动设置章节排序: 项目ID={}, 新排序={}", projectId, sortOrder);
            return;
        }

        // 检查sortOrder是否合法（必须大于0）
        if (sortOrder <= 0) {
            throw new IllegalArgumentException("章节排序必须大于0");
        }

        // 查询是否存在相同sortOrder的章节
        Chapter existingChapter = chapterMapper.selectByProjectIdAndOrder(projectId, sortOrder);
        
        if (existingChapter != null) {
            // 如果是更新操作且是同一个章节，则允许
            if (isUpdate && existingChapter.getId().equals(chapter.getId())) {
                log.info("更新章节保持原有排序: 项目ID={}, 章节ID={}, 排序={}", 
                         projectId, chapter.getId(), sortOrder);
                return;
            }

            // 存在重复，需要调整后续章节的sortOrder
            log.warn("检测到章节排序重复: 项目ID={}, 重复排序={}, 将调整后续章节排序", projectId, sortOrder);
            adjustSubsequentChapterOrders(projectId, sortOrder, chapter.getId());
        }

        log.info("验证章节排序通过: 项目ID={}, 章节排序={}", projectId, sortOrder);
    }

    /**
     * 获取项目中下一个可用的sortOrder
     *
     * @param projectId 项目ID
     * @return 下一个可用的sortOrder
     */
    private Integer getNextAvailableSortOrder(Long projectId) {
        LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Chapter::getProjectId, projectId);
        queryWrapper.orderByDesc(Chapter::getSortOrder);
        queryWrapper.last("LIMIT 1");
        
        Chapter lastChapter = getOne(queryWrapper);
        return lastChapter != null ? lastChapter.getSortOrder() + 1 : 1;
    }

    /**
     * 调整指定sortOrder及其后续章节的排序，为新章节腾出位置
     *
     * @param projectId 项目ID
     * @param startSortOrder 开始调整的sortOrder
     * @param excludeChapterId 要排除的章节ID（新增章节时为null，更新时为当前章节ID）
     */
    private void adjustSubsequentChapterOrders(Long projectId, Integer startSortOrder, Long excludeChapterId) {
        try {
            // 查询需要调整的章节（从指定sortOrder开始的所有章节，按sortOrder升序）
            LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Chapter::getProjectId, projectId);
            queryWrapper.ge(Chapter::getSortOrder, startSortOrder);
            if (excludeChapterId != null) {
                queryWrapper.ne(Chapter::getId, excludeChapterId);
            }
            queryWrapper.orderByAsc(Chapter::getSortOrder);
            
            List<Chapter> chaptersToAdjust = list(queryWrapper);
            
            if (chaptersToAdjust.isEmpty()) {
                log.info("没有需要调整排序的章节: 项目ID={}, 起始排序={}", projectId, startSortOrder);
                return;
            }

            log.info("开始调整章节排序: 项目ID={}, 影响章节数量={}, 起始排序={}", 
                     projectId, chaptersToAdjust.size(), startSortOrder);

            // 从后往前调整，避免排序冲突
            // 给每个章节的sortOrder加1，为新章节腾出位置
            for (int i = chaptersToAdjust.size() - 1; i >= 0; i--) {
                Chapter chapterToAdjust = chaptersToAdjust.get(i);
                Integer newSortOrder = chapterToAdjust.getSortOrder() + 1;
                
                log.debug("调整章节排序: ID={}, 原排序={}, 新排序={}", 
                         chapterToAdjust.getId(), chapterToAdjust.getSortOrder(), newSortOrder);
                
                chapterToAdjust.setSortOrder(newSortOrder);
                chapterToAdjust.setUpdatedAt(LocalDateTime.now());
                updateById(chapterToAdjust);
            }

            log.info("章节排序调整完成: 项目ID={}, 调整了{}个章节", projectId, chaptersToAdjust.size());
            
        } catch (Exception e) {
            log.error("调整章节排序失败: 项目ID={}, 起始排序={}, 错误={}", 
                     projectId, startSortOrder, e.getMessage(), e);
            throw new RuntimeException("调整章节排序失败: " + e.getMessage());
        }
    }

    /**
     * 重新整理项目中所有章节的sortOrder，确保连续且无重复
     * 
     * @param projectId 项目ID
     * @return 重新整理的章节数量
     */
    @Override
    public int reorderChaptersByProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("项目ID不能为空");
        }

        try {
            // 查询项目所有章节，按当前sortOrder排序
            LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Chapter::getProjectId, projectId);
            queryWrapper.orderByAsc(Chapter::getSortOrder);
            
            List<Chapter> chapters = list(queryWrapper);
            
            if (chapters.isEmpty()) {
                log.info("项目没有章节需要重新排序: 项目ID={}", projectId);
                return 0;
            }

            log.info("开始重新整理项目章节排序: 项目ID={}, 章节总数={}", projectId, chapters.size());

            // 重新分配sortOrder，从1开始连续递增
            LocalDateTime now = LocalDateTime.now();
            int reorderedCount = 0;
            
            for (int i = 0; i < chapters.size(); i++) {
                Chapter chapter = chapters.get(i);
                Integer newSortOrder = i + 1;
                
                // 只有当sortOrder发生变化时才更新
                if (!newSortOrder.equals(chapter.getSortOrder())) {
                    log.debug("重新排序章节: ID={}, 标题={}, 原排序={}, 新排序={}", 
                             chapter.getId(), chapter.getTitle(), chapter.getSortOrder(), newSortOrder);
                    
                    chapter.setSortOrder(newSortOrder);
                    chapter.setUpdatedAt(now);
                    updateById(chapter);
                    reorderedCount++;
                }
            }

            log.info("项目章节排序整理完成: 项目ID={}, 更新了{}个章节", projectId, reorderedCount);
            return reorderedCount;
            
        } catch (Exception e) {
            log.error("重新整理项目章节排序失败: 项目ID={}, 错误={}", projectId, e.getMessage(), e);
            throw new RuntimeException("重新整理章节排序失败: " + e.getMessage());
        }
    }

} 