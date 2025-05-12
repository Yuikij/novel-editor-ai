package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.mapper.PlotMapper;
import com.soukon.novelEditorAi.service.PlotService;
import com.soukon.novelEditorAi.service.CharacterService;
import com.soukon.novelEditorAi.service.ChapterService;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
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
public class PlotServiceImpl extends ServiceImpl<PlotMapper, Plot> implements PlotService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
    @Autowired
    private CharacterService characterService;
    
    @Autowired
    private ChapterService chapterService;
    
    private final PlotMapper plotMapper;
    private final ChatClient chatClient;
    
    @Autowired
    public PlotServiceImpl(PlotMapper plotMapper, CharacterService characterService, 
                          ChapterService chapterService, org.springframework.ai.chat.model.ChatModel openAiChatModel) {
        this.plotMapper = plotMapper;
        this.characterService = characterService;
        this.chapterService = chapterService;
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
     * 生成用于构建生成请求 Prompt 的单个情节信息。
     *
     * @param plot 情节实体
     * @return 包含情节描述的字符串，以列表项格式输出。
     */
    @Override
    public String toPrompt(Plot plot) {
        if (plot == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (plot.getTitle() != null && !plot.getTitle().isEmpty()) {
            sb.append("情节标题: ").append(plot.getTitle()).append("\n");
        }
        if (plot.getType() != null && !plot.getType().isEmpty()) {
            sb.append("类型: ").append(plot.getType()).append("\n");
        }
        if (plot.getDescription() != null && !plot.getDescription().isEmpty()) {
            sb.append("描述: ").append(plot.getDescription()).append("\n");
        }
        if (plot.getStatus() != null && !plot.getStatus().isEmpty()) {
            sb.append("完成情况: ").append(plot.getStatus()).append("\n");
        }
        if (plot.getCompletionPercentage() != null) {
            sb.append("完成百分比: ").append(plot.getCompletionPercentage()).append("\n");
        }
        if (plot.getWordCountGoal() != null) {
            sb.append("目标字数: ").append(plot.getWordCountGoal()).append("\n");
        }
        if (plot.getCharacterIds() != null && !plot.getCharacterIds().isEmpty()) {
            sb.append("涉及角色: ");
            for (Long cid : plot.getCharacterIds()) {
                String name = null;
                if (cid != null) {
                    com.soukon.novelEditorAi.entities.Character character = characterService.getById(cid);
                    name = (character != null && character.getName() != null) ? character.getName() : ("ID[" + cid + "]");
                }
                sb.append(name).append(", ");
            }
            // 去掉最后一个逗号和空格
            if (!sb.isEmpty() && sb.charAt(sb.length() - 2) == ',') {
                sb.delete(sb.length() - 2, sb.length());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toPrompt(Long chapterId) {
        List<Plot> plots = list(lambdaQuery().eq(Plot::getChapterId, chapterId));
        StringBuilder plotsInfo = new StringBuilder();
        if (plots != null && !plots.isEmpty()) {
            plotsInfo.append("情节列表").append(":\n");
            for (Plot plot : plots) {
                // 使用直接查库的toPrompt方法，自动获取上一章节摘要
                plotsInfo.append(toPrompt(plot));
                plotsInfo.append("-----\n");
            }
        }
        return plotsInfo.toString();
    }
    
    /**
     * 根据已有的情节，补全或扩展情节列表到目标数量
     *
     * @param chapterId         章节ID
     * @param existingPlotIds    已有的情节ID列表
     * @param targetCount        目标情节总数
     * @return 补全后的情节列表（包含已有的和新生成的）
     */
    @Override
    public List<Plot> expandPlots(Long chapterId, List<Long> existingPlotIds, Integer targetCount) {
        // 如果未指定目标数量，设置默认值为5
        if (targetCount == null || targetCount < 1) {
            targetCount = 5;
        }

        // 获取已有的情节
        List<Plot> existingPlots = new ArrayList<>();
        if (existingPlotIds != null && !existingPlotIds.isEmpty()) {
            existingPlots = this.listByIds(existingPlotIds);
            // 按照sortOrder排序，确保序列正确
            existingPlots.sort(Comparator.comparing(Plot::getSortOrder));
        }

        // 如果已经达到或超过目标数量，直接返回
        if (existingPlots.size() >= targetCount) {
            return existingPlots;
        }

        // 获取章节信息
        Chapter chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("找不到指定的章节: " + chapterId);
        }

        // 调用LLM补全情节
        List<Plot> newPlots = callLlmForPlotExpansion(chapter, existingPlots, targetCount);

        // 设置基本属性并保存到数据库
        int sortOrder = existingPlots.isEmpty() ? 1 :
                existingPlots.stream().mapToInt(Plot::getSortOrder).max().orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();

        for (Plot plot : newPlots) {
            plot.setChapterId(chapterId);
            plot.setProjectId(chapter.getProjectId());
            plot.setSortOrder(sortOrder++);
            plot.setCreatedAt(now);
            plot.setUpdatedAt(now);
            plot.setStatus("draft");
            // 根据章节的目标字数平均分配情节字数目标
            if (chapter.getWordCountGoal() != null) {
                int totalPlots = existingPlots.size() + newPlots.size();
                int wordCountGoal = (int)(chapter.getWordCountGoal() / totalPlots);
                plot.setWordCountGoal(wordCountGoal);
            }
            // 保存到数据库
            this.save(plot);
        }

        // 合并已有的和新生成的情节，并按sortOrder排序
        List<Plot> allPlots = new ArrayList<>(existingPlots);
        allPlots.addAll(newPlots);
        allPlots.sort(Comparator.comparing(Plot::getSortOrder));

        return allPlots;
    }

    /**
     * 调用LLM补全或扩展情节列表
     *
     * @param chapter          章节信息
     * @param existingPlots    已有的情节列表
     * @param targetCount      目标情节总数
     * @return 生成的新情节列表
     */
    private List<Plot> callLlmForPlotExpansion(Chapter chapter, List<Plot> existingPlots, Integer targetCount) {
        // 获取项目ID
        Long projectId = chapter.getProjectId();
        
        // 获取项目所有角色，以便LLM能够参考项目中的角色名称
        List<com.soukon.novelEditorAi.entities.Character> projectCharacters = getProjectCharacters(projectId);
        
        // 系统提示词 - 引导AI扩展情节
        String systemPrompt = """
                你是一个专业的小说情节规划助手，帮助作者规划章节内的具体情节。
                请根据已有的情节列表和章节基本信息，补充生成新的情节规划，使总情节数达到目标数量。
                                
                每个情节应包含以下信息：
                1. 情节标题：简洁且能反映情节内容的标题
                2. 情节描述：详细描述这个情节中发生的事件、冲突和角色互动
                3. 情节类型：如"开场"、"转折"、"高潮"、"结局"等
                4. 完成情况：默认为"draft"
                5. 完成百分比：默认为0
                6. 目标字数：根据章节总字数和情节数量合理分配
                7. 关联角色：列出在该情节中出现的角色名称，这些名称必须从我提供的可用角色列表中选择
                                
                请确保新生成的情节与已有情节在逻辑上连贯，形成完整的章节故事弧线。情节应该有起承转合，推动故事发展。
                请严格按照JSON格式返回结果，包含下面所有字段：
                
                格式示例：
                [
                  {
                    "title": "情节标题",
                    "description": "情节详细描述",
                    "type": "情节类型",
                    "status": "draft",
                    "completionPercentage": 0,
                    "wordCountGoal": 500,
                    "characterNames": ["角色1", "角色2"]
                  }
                ]
                """;

        // 用户提示词 - 构建上下文和请求
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("## 章节基本信息\n");
        userPromptBuilder.append(chapterService.toPrompt(chapter)).append("\n");

        // 添加已有情节
        userPromptBuilder.append("\n## 已有情节\n");
        if (existingPlots.isEmpty()) {
            userPromptBuilder.append("当前章节还没有已有情节，请创建全新的情节规划。\n");
        } else {
            for (int i = 0; i < existingPlots.size(); i++) {
                Plot plot = existingPlots.get(i);
                userPromptBuilder.append(i + 1).append(". ");
                userPromptBuilder.append("【").append(plot.getTitle()).append("】");
                userPromptBuilder.append(" 描述: ").append(plot.getDescription() != null ? plot.getDescription() : "无描述");
                userPromptBuilder.append(" 类型: ").append(plot.getType() != null ? plot.getType() : "无类型");
                
                // 添加额外信息
                userPromptBuilder.append(" 目标字数: ").append(plot.getWordCountGoal() != null ? plot.getWordCountGoal() : "未设置");
                
                // 添加角色信息
                if (plot.getCharacterIds() != null && !plot.getCharacterIds().isEmpty()) {
                    userPromptBuilder.append(" 关联角色: [");
                    List<String> characterNames = new ArrayList<>();
                    for (Long characterId : plot.getCharacterIds()) {
                        com.soukon.novelEditorAi.entities.Character character = characterService.getById(characterId);
                        if (character != null) {
                            characterNames.add(character.getName());
                        }
                    }
                    userPromptBuilder.append(String.join(", ", characterNames));
                    userPromptBuilder.append("]");
                }
                
                userPromptBuilder.append("\n");
            }
        }

        // 添加可用的角色列表
        userPromptBuilder.append("\n## 可用角色列表\n");
        if (projectCharacters.isEmpty()) {
            userPromptBuilder.append("项目中尚未定义角色，可以在情节中创建新角色。\n");
        } else {
            userPromptBuilder.append("请从以下角色列表中选择角色参与情节：\n");
            for (com.soukon.novelEditorAi.entities.Character character : projectCharacters) {
                userPromptBuilder.append("- ").append(character.getName())
                        .append("（").append(character.getRole() != null ? character.getRole() : "未知角色类型").append("）");
                if (character.getDescription() != null && !character.getDescription().isEmpty()) {
                    userPromptBuilder.append("：").append(character.getDescription());
                }
                userPromptBuilder.append("\n");
            }
        }

        userPromptBuilder.append("\n请创建约").append(targetCount - existingPlots.size())
                .append("个新情节，使总数达到").append(targetCount).append("个。");
        
        // 添加关于字数的指导
        int averageWordCount = 0;
        if (chapter.getWordCountGoal() != null) {
            int totalPlots = Math.max(targetCount, 1);
            averageWordCount = (int)(chapter.getWordCountGoal() / totalPlots);
            userPromptBuilder.append("\n\n章节总目标字数为").append(chapter.getWordCountGoal())
                    .append("字，平均每个情节约").append(averageWordCount).append("字。");
        }
        
        // 添加一般指导说明
        userPromptBuilder.append("\n\n请确保情节之间有逻辑连贯性，形成完整的故事弧线。");
        userPromptBuilder.append("情节应该从章节开始，经过适当的冲突和转折，到达章节的高潮和结局。");
        userPromptBuilder.append("\n\n每个情节都应指定参与的角色（characterNames字段），仅使用上述角色列表中的角色名称。");

        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));
        log.info("AI扩展情节请求: {}", messages);

        try {
            // 发送请求到AI服务
            Prompt prompt = new Prompt(messages);
            String response = chatClient.prompt(prompt).call().content();
            log.info("AI扩展情节响应: {}", response);

            // 解析JSON响应
            List<PlotDto> plotDtos = parsePlotDtosFromJson(response);
            
            // 转换为Plot实体并处理角色名称到ID的映射
            return convertPlotDtosToEntities(plotDtos, projectCharacters, projectId, averageWordCount);
        } catch (Exception e) {
            log.error("调用AI扩展情节失败: {}", e.getMessage(), e);
            // 失败时返回空列表
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取项目中所有角色
     */
    private List<com.soukon.novelEditorAi.entities.Character> getProjectCharacters(Long projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        
        // 使用LambdaQueryWrapper查询指定项目的所有角色
        return characterService.list(
            new LambdaQueryWrapper<com.soukon.novelEditorAi.entities.Character>()
                .eq(com.soukon.novelEditorAi.entities.Character::getProjectId, projectId)
        );
    }
    
    /**
     * 定义DTO类用于解析LLM响应
     */
    private static class PlotDto {
        private String title;
        private String description;
        private String type;
        private String status;
        private Integer completionPercentage;
        private Integer wordCountGoal;
        private List<String> characterNames;
        
        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Integer getCompletionPercentage() { return completionPercentage; }
        public void setCompletionPercentage(Integer completionPercentage) { this.completionPercentage = completionPercentage; }
        
        public Integer getWordCountGoal() { return wordCountGoal; }
        public void setWordCountGoal(Integer wordCountGoal) { this.wordCountGoal = wordCountGoal; }
        
        public List<String> getCharacterNames() { return characterNames; }
        public void setCharacterNames(List<String> characterNames) { this.characterNames = characterNames; }
    }
    
    /**
     * 从JSON字符串解析PlotDto列表
     */
    private List<PlotDto> parsePlotDtosFromJson(String json) {
        try {
            // 提取JSON部分
            String jsonContent = extractJsonFromString(json);
            
            // 解析JSON到DTO对象列表
            return new ObjectMapper().readValue(jsonContent,
                    new TypeReference<List<PlotDto>>() {});
        } catch (Exception e) {
            log.error("解析情节DTO JSON失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 将PlotDto转换为Plot实体
     */
    private List<Plot> convertPlotDtosToEntities(List<PlotDto> dtos, 
                                               List<com.soukon.novelEditorAi.entities.Character> projectCharacters,
                                               Long projectId, 
                                               int defaultWordCount) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Plot> plots = new ArrayList<>();
        
        // 创建角色名称到ID的映射
        Map<String, Long> characterNameToIdMap = new HashMap<>();
        for (com.soukon.novelEditorAi.entities.Character character : projectCharacters) {
            characterNameToIdMap.put(character.getName(), character.getId());
        }
        
        for (PlotDto dto : dtos) {
            Plot plot = new Plot();
            
            // 设置基本字段
            plot.setTitle(dto.getTitle());
            plot.setDescription(dto.getDescription());
            plot.setType(dto.getType());
            plot.setStatus(dto.getStatus() != null ? dto.getStatus() : "draft");
            plot.setCompletionPercentage(dto.getCompletionPercentage() != null ? dto.getCompletionPercentage() : 0);
            
            // 设置目标字数
            if (dto.getWordCountGoal() != null) {
                plot.setWordCountGoal(dto.getWordCountGoal());
            } else if (defaultWordCount > 0) {
                plot.setWordCountGoal(defaultWordCount);
            }
            
            // 处理角色ID
            if (dto.getCharacterNames() != null && !dto.getCharacterNames().isEmpty()) {
                List<Long> characterIds = new ArrayList<>();
                
                for (String name : dto.getCharacterNames()) {
                    // 从映射中查找角色ID
                    Long id = characterNameToIdMap.get(name);
                    if (id != null) {
                        characterIds.add(id);
                    } else {
                        log.warn("未找到角色名称对应的ID: {}", name);
                    }
                }
                
                if (!characterIds.isEmpty()) {
                    plot.setCharacterIds(characterIds);
                }
            }
            
            plots.add(plot);
        }
        
        return plots;
    }

    /**
     * 从JSON字符串解析情节列表
     *
     * @param json JSON格式的情节列表字符串
     * @return 解析后的情节对象列表
     */
    @Deprecated
    private List<Plot> parsePlotsFromJson(String json) {
        try {
            // 提取JSON部分
            String jsonContent = extractJsonFromString(json);

            // 解析JSON到情节对象列表
            return new ObjectMapper().readValue(jsonContent,
                    new TypeReference<List<Plot>>() {
                    });
        } catch (Exception e) {
            log.error("解析情节JSON失败: {}", e.getMessage(), e);
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
     * 获取章节中第一个未完成的情节（完成度不是100%）
     *
     * @param chapterId 章节ID
     * @return 第一个未完成的情节，如果所有情节都已完成或没有情节则返回null
     */
    @Override
    public Plot getFirstIncompletePlot(Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        
        // 创建查询条件：按章节ID过滤、完成百分比小于100、按sortOrder排序取第一个
        LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Plot::getChapterId, chapterId)
                    .lt(Plot::getCompletionPercentage, 100)
                    .orderByAsc(Plot::getSortOrder)
                    .last("LIMIT 1");
        
        return getOne(queryWrapper);
    }
} 