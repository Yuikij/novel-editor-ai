package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.entities.CharacterRelationship;
import com.soukon.novelEditorAi.entities.OutlinePlotPoint;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.mapper.OutlinePlotPointMapper;
import com.soukon.novelEditorAi.service.CharacterRelationshipService;
import com.soukon.novelEditorAi.service.CharacterService;
import com.soukon.novelEditorAi.service.OutlinePlotPointService;
import com.soukon.novelEditorAi.service.ProjectService;
import com.soukon.novelEditorAi.service.WorldService;
import com.soukon.novelEditorAi.service.ChapterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class OutlinePlotPointServiceImpl extends ServiceImpl<OutlinePlotPointMapper, OutlinePlotPoint> implements OutlinePlotPointService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed

    private final ChatModel openAiChatModel;
    private final ObjectMapper objectMapper;
    
    private final ProjectService projectService;
    private final CharacterService characterService;
    private final CharacterRelationshipService characterRelationshipService;
    private final ChapterService chapterService;
    
    @Autowired
    public OutlinePlotPointServiceImpl(@Qualifier("openAiChatModel") ChatModel openAiChatModel,
                                      ObjectMapper objectMapper,
                                      ProjectService projectService,
                                      WorldService worldService,
                                      CharacterService characterService,
                                      CharacterRelationshipService characterRelationshipService,
                                      @Lazy ChapterService chapterService) {
        this.openAiChatModel = openAiChatModel;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
        this.characterService = characterService;
        this.characterRelationshipService = characterRelationshipService;
        this.chapterService = chapterService;
    }

    /**
     * 生成用于构建生成请求 Prompt 的大纲情节点信息。
     *
     * @param point 大纲情节点实体
     * @return 包含情节点描述的字符串。
     */
    @Override
    public String toPrompt(OutlinePlotPoint point) {
        if (point == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (point.getTitle() != null && !point.getTitle().isEmpty()) {
            sb.append("标题: ").append(point.getTitle());
        }
        if (point.getType() != null && !point.getType().isEmpty()) {
            sb.append(" [类型: ").append(point.getType()).append("]");
        }
        if (point.getDescription() != null && !point.getDescription().isEmpty()) {
            sb.append(" 描述: ").append(point.getDescription());
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public String toPrompt(Long projectId) {
        LambdaQueryWrapper<OutlinePlotPoint> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OutlinePlotPoint::getProjectId, projectId);
        queryWrapper.orderByAsc(OutlinePlotPoint::getSortOrder);
        List<OutlinePlotPoint> outlinePlotPoints = list(queryWrapper);
        StringBuilder chaptersInfo = new StringBuilder();
        if (outlinePlotPoints != null && !outlinePlotPoints.isEmpty()) {
            chaptersInfo.append("大纲 ").append(":\n");
            for (OutlinePlotPoint  outlinePlotPoint: outlinePlotPoints) {
                // 使用直接查库的toPrompt方法，自动获取上一章节摘要
                chaptersInfo.append(toPrompt(outlinePlotPoint));
                chaptersInfo.append("-----\n");
            }
        }
        return chaptersInfo.toString();
    }


    /**
     * 构建小说上下文信息
     * 
     * @param projectId 项目ID
     * @return 包含小说上下文的Map
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
            
            // 获取角色信息
            context.put("主要角色", characterService.toPrompt(projectId));
            
            // 获取角色关系
            context.put("角色关系", characterRelationshipService.toPrompt(projectId));

            // 获取章节信息
            context.put("章节信息", chapterService.toPromptProjectId(projectId));
            return context;
        } catch (Exception e) {
            log.error("构建小说上下文失败", e);
            throw new RuntimeException("构建小说上下文失败: " + e.getMessage());
        }
    }

    /**
     * 根据已有的大纲情节点，补全或扩展情节点列表
     *
     * @param projectId 项目ID
     * @param existingPlotPointIds 已有的情节点ID列表
     * @param targetCount 目标情节点总数
     * @return 补全后的情节点列表（包含已有的和新生成的）
     */
    @Override
    public List<OutlinePlotPoint> expandOutlinePlotPoints(Long projectId, List<Long> existingPlotPointIds, Integer targetCount) {
        // 如果未指定目标数量，设置默认值为12
        if (targetCount == null || targetCount < 1) {
            targetCount = 12;
        }
        
        // 获取已有的情节点
        List<OutlinePlotPoint> existingPoints = new ArrayList<>();
        if (existingPlotPointIds != null && !existingPlotPointIds.isEmpty()) {
            existingPoints = this.listByIds(existingPlotPointIds);
            // 按照sortOrder排序，确保序列正确
            existingPoints.sort(Comparator.comparing(OutlinePlotPoint::getSortOrder));
        }
        
        // 如果已经达到或超过目标数量，直接返回
        if (existingPoints.size() >= targetCount) {
            return existingPoints;
        }
        
        // 调用LLM补全情节点
        List<OutlinePlotPoint> newPoints = callLlmForOutlineExpansion(projectId, existingPoints, targetCount);
        
        // 设置基本属性并保存到数据库
        int sortOrder = existingPoints.isEmpty() ? 1 : 
                existingPoints.stream().mapToInt(p -> p.getSortOrder()).max().orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();
        
        for (OutlinePlotPoint point : newPoints) {
            point.setProjectId(projectId);
            point.setSortOrder(sortOrder++);
            point.setCreatedAt(now);
            point.setUpdatedAt(now);
            // 保存到数据库
            this.save(point);
        }
        
        // 合并已有的和新生成的情节点，并按sortOrder排序
        List<OutlinePlotPoint> allPoints = new ArrayList<>(existingPoints);
        allPoints.addAll(newPoints);
        allPoints.sort((a, b) -> a.getSortOrder().compareTo(b.getSortOrder()));
        
        return allPoints;
    }
    
    /**
     * 调用LLM补全或扩展大纲情节点列表
     * 
     * @param projectId 项目ID
     * @param existingPoints 已有的情节点列表
     * @param targetCount 目标情节点总数
     * @return 生成的新情节点列表
     */
    private List<OutlinePlotPoint> callLlmForOutlineExpansion(Long projectId, List<OutlinePlotPoint> existingPoints, Integer targetCount) {
        // 构建小说上下文信息
        Map<String, Object> context = buildNovelContext(projectId);
        
        // 系统提示词 - 引导AI扩展大纲
        String systemPrompt = """
                你是一位专业的小说大纲设计专家，擅长根据已有情节点扩展和完善故事大纲。
                
                请根据提供的小说背景信息和已有的情节点，补充添加新的情节点，使故事更加完整和连贯。
                已有情节点应该保持不变，你需要在现有基础上，补充更多的情节点。
                
                遵循以下规则:
                1. 保持故事的连贯性和逻辑性，新增的情节点应该与已有情节点自然衔接
                2. 确保故事整体结构平衡，包含起始、发展、高潮和结局各个阶段
                3. 每个情节点提供简洁但有足够信息量的标题和描述
                4. 注意情节发展应遵循合理的因果关系
                5. 新添加的情节点应当与已有情节点风格一致，并符合小说的背景设定
                
                返回格式必须是结构化的JSON数组，只包含新增的情节点，每个情节点包含以下字段：
                [
                  {
                    "title": "情节点标题",
                    "type": "起始|发展|高潮|结局|其他",
                    "description": "详细描述这个情节点的内容和意义"
                  },
                  ...
                ]
                
                请确保只返回新增加的情节点，而不包含已有的情节点。总数量应该使总体情节点达到大约%d个。
                """.formatted(targetCount);
        
        // 构建用户提示词
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("请根据以下小说信息和已有情节点，补充生成新的情节点：\n\n");
        
        // 添加小说上下文信息
        userPromptBuilder.append("## 小说信息\n");
        context.forEach((key, value) -> {
            if (value != null && !value.toString().isEmpty()) {
                userPromptBuilder.append(key).append(": ").append(value).append("\n");
            }
        });
        
        // 添加已有情节点
        userPromptBuilder.append("\n## 已有情节点\n");
        if (existingPoints.isEmpty()) {
            userPromptBuilder.append("当前没有已有情节点，请创建全新的大纲。\n");
        } else {
            for (int i = 0; i < existingPoints.size(); i++) {
                OutlinePlotPoint point = existingPoints.get(i);
                userPromptBuilder.append(i + 1).append(". ");
                userPromptBuilder.append("【").append(point.getTitle()).append("】");
                userPromptBuilder.append(" [类型: ").append(point.getType()).append("] ");
                userPromptBuilder.append("描述: ").append(point.getDescription()).append("\n");
            }
        }
        
        userPromptBuilder.append("\n请创建约").append(targetCount - existingPoints.size())
                .append("个新情节点，使总数达到").append(targetCount).append("个左右。");
        
        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));
        log.info("AI扩展大纲情节点请求: {}", messages);
        try {
            // 调用LLM获取响应
            ChatClient chatClient = ChatClient.builder(openAiChatModel)
                    .defaultAdvisors(new SimpleLoggerAdvisor())
                    .defaultOptions(
                            OpenAiChatOptions.builder()
                                    .temperature(0.7)
                                    .build()
                    )
                    .build();
                    
            String response = chatClient.prompt(new Prompt(messages)).call().content();
            log.info("AI扩展大纲情节点响应: {}", response);
            // 解析JSON响应
            return objectMapper.readValue(response, new TypeReference<List<OutlinePlotPoint>>() {});
        } catch (Exception e) {
            log.error("扩展大纲情节点失败", e);
            throw new RuntimeException("调用AI扩展大纲失败: " + e.getMessage());
        }
    }
} 