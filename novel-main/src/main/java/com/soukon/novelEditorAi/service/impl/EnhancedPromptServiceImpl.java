package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.ChapterContext;
import com.soukon.novelEditorAi.model.chapter.PlanRes;
import com.soukon.novelEditorAi.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强版提示词服务
 * 专注于生成更高质量、更文学化的写作计划
 */
@Service
@Slf4j
public class EnhancedPromptServiceImpl {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ChapterService chapterService;
    @Autowired
    private WorldService worldService;
    @Autowired
    private CharacterService characterService;
    @Autowired
    private PlotService plotService;
    @Autowired
    private CharacterRelationshipService characterRelationshipService;
    @Autowired
    private OutlinePlotPointService outlinePlotPointService;

    // 添加无参构造函数和带参构造函数
    public EnhancedPromptServiceImpl() {
        // 默认构造函数
    }
    
    public EnhancedPromptServiceImpl(ProjectService projectService,
                                   ChapterService chapterService,
                                   WorldService worldService,
                                   CharacterService characterService,
                                   PlotService plotService,
                                   CharacterRelationshipService characterRelationshipService,
                                   OutlinePlotPointService outlinePlotPointService) {
        this.projectService = projectService;
        this.chapterService = chapterService;
        this.worldService = worldService;
        this.characterService = characterService;
        this.plotService = plotService;
        this.characterRelationshipService = characterRelationshipService;
        this.outlinePlotPointService = outlinePlotPointService;
    }

    /**
     * 构建增强版写作计划提示词
     */
    public List<Message> buildEnhancedPlanningPrompt(ChapterContentRequest request) {
        ChapterContext chapterContext = request.getChapterContext();
        
        if (chapterContext == null) {
            throw new IllegalStateException("章节上下文未构建");
        }
        
        BeanOutputConverter<PlanRes> converter = new BeanOutputConverter<>(PlanRes.class);
        
        // 系统提示词 - 更专业的文学创作指导
        String systemPrompt = """
            你是一位资深的文学编辑和创作导师，拥有丰富的小说创作和指导经验。
            
            你的任务是根据提供的小说上下文，制定一个高质量的写作计划。这个计划应该：
            
            ## 核心原则
            1. **文学性优先**：每个步骤都要考虑文学价值，而不仅仅是情节推进
            2. **节奏控制**：合理安排叙事节奏，做到张弛有度
            3. **情感深度**：注重人物内心世界的挖掘和情感的细腻表达
            4. **细节丰富**：通过具体的细节来营造氛围和推进情节
            5. **语言美感**：追求语言的优美和表达的精准
            
            ## 计划要求
            - 将写作任务分解为3-6个逻辑清晰的步骤
            - 每个步骤都有明确的文学目标（不仅仅是情节目标）
            - 字数分配要合理，确保每个步骤都有足够的发挥空间
            - 步骤之间要有良好的衔接和递进关系
            - 最后一个步骤要为后续章节留下合适的悬念或转折
            
            ## 避免的问题
            - 避免机械化的情节推进
            - 避免过于直白的叙述
            - 避免忽视人物的内心活动
            - 避免缺乏环境和氛围的营造
            - 避免语言平淡无味
            
            输出格式：{%s}
            """.formatted(converter.getFormat());
        
        // 用户提示词 - 包含丰富的上下文信息
        String userPrompt = buildContextualUserPrompt(request, chapterContext);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));
        
        log.info("[Enhanced Planning] 增强版计划提示词已构建");
        return messages;
    }
    
    /**
     * 构建上下文化的用户提示词
     */
    private String buildContextualUserPrompt(ChapterContentRequest request, ChapterContext context) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // 1. 小说基本信息
        promptBuilder.append("## 小说基本信息\n");
        if (context.getProject() != null) {
            promptBuilder.append("**作品名称**：").append(context.getProject().getTitle()).append("\n");
            promptBuilder.append("**作品类型**：").append(context.getProject().getGenre()).append("\n");
            promptBuilder.append("**写作风格**：").append(context.getProject().getStyle()).append("\n");
            promptBuilder.append("**作品简介**：").append(context.getProject().getSynopsis()).append("\n\n");
        }
        
        // 2. 当前章节信息
        promptBuilder.append("## 当前章节信息\n");
        Chapter currentChapter = context.getCurrentChapter();
        if (currentChapter != null) {
            promptBuilder.append("**章节标题**：").append(currentChapter.getTitle()).append("\n");
            promptBuilder.append("**章节摘要**：").append(currentChapter.getSummary()).append("\n");
            promptBuilder.append("**目标字数**：").append(request.getWordCountSuggestion()).append("字\n");
            promptBuilder.append("**写作建议**：").append(request.getPromptSuggestion()).append("\n\n");
        }
        
        // 3. 故事背景和世界观
        if (context.getWorld() != null) {
            promptBuilder.append("## 故事背景\n");
            promptBuilder.append(worldService.toPrompt(context.getWorld())).append("\n");
        }
        
        // 4. 主要角色信息
        if (context.getCharacters() != null && !context.getCharacters().isEmpty()) {
            promptBuilder.append("## 主要角色\n");
            context.getCharacters().forEach(character -> 
                promptBuilder.append(characterService.toPrompt(character)).append("\n"));
        }
        
        // 5. 角色关系
        if (context.getCharacterRelationships() != null && !context.getCharacterRelationships().isEmpty()) {
            promptBuilder.append("## 角色关系\n");
            context.getCharacterRelationships().forEach(rel -> 
                promptBuilder.append(characterRelationshipService.toPrompt(rel)).append("\n"));
        }
        
        // 6. 前文回顾
        promptBuilder.append("## 前文回顾\n");
        if (context.getPreviousChapter() != null) {
            promptBuilder.append("**上一章摘要**：").append(context.getPreviousChapter().getSummary()).append("\n");
        }
        
        // 7. 当前章节已有内容
        if (currentChapter != null && currentChapter.getContent() != null && !currentChapter.getContent().trim().isEmpty()) {
            promptBuilder.append("## 已有内容\n");
            String existingContent = currentChapter.getContent();
            // 如果内容过长，只显示最后部分
            if (existingContent.length() > 2000) {
                existingContent = "...\n" + existingContent.substring(existingContent.length() - 2000);
            }
            promptBuilder.append(existingContent).append("\n\n");
            promptBuilder.append("**续写要求**：请在已有内容基础上继续创作\n\n");
        } else {
            promptBuilder.append("**创作要求**：从头开始创作本章节\n\n");
        }
        
        // 8. 当前情节要求
        Plot currentPlot = plotService.getFirstIncompletePlot(currentChapter.getId());
        if (currentPlot != null) {
            request.setCurrentPlot(currentPlot);
            promptBuilder.append("## 当前情节要求\n");
            promptBuilder.append("**情节描述**：").append(currentPlot.getDescription()).append("\n");
            promptBuilder.append("**情节字数目标**：").append(currentPlot.getWordCountGoal()).append("字\n");
            if (currentPlot.getCharacterIds() != null && !currentPlot.getCharacterIds().isEmpty()) {
                promptBuilder.append("**涉及角色**：").append(plotService.toCharacter(currentPlot)).append("\n");
            }
            promptBuilder.append("\n");
        }
        
        // 9. 写作任务
        promptBuilder.append("## 写作任务\n");
        promptBuilder.append("请根据以上信息，制定一个高质量的写作计划。计划应该：\n");
        promptBuilder.append("1. 体现文学性和艺术性\n");
        promptBuilder.append("2. 合理控制叙事节奏\n");
        promptBuilder.append("3. 深入挖掘人物内心\n");
        promptBuilder.append("4. 营造生动的场景氛围\n");
        promptBuilder.append("5. 推进情节发展\n");
        promptBuilder.append("6. 为后续章节做好铺垫\n\n");
        
        promptBuilder.append("**重要提醒**：请直接输出JSON格式的计划，不要包含任何额外的解释或说明。\n");
        
        return promptBuilder.toString();
    }
    
    /**
     * 构建文学风格分析提示词
     */
    public List<Message> buildStyleAnalysisPrompt(String referenceText) {
        String systemPrompt = """
            你是一位文学风格分析专家，擅长识别和分析文本的写作特征。
            
            请分析提供的文本样本，识别以下特征：
            1. **语言特色**：词汇选择、句式结构、修辞手法
            2. **叙事风格**：视角、语调、节奏感
            3. **描写技巧**：环境描写、人物刻画、心理描写的特点
            4. **文学手法**：象征、隐喻、对比等手法的运用
            5. **整体风格**：文学流派、时代特征、个人特色
            
            分析结果将用于指导后续的创作，请给出具体、可操作的特征描述。
            """;
        
        String userPrompt = "请分析以下文本的写作风格：\n\n" + referenceText;
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));
        
        return messages;
    }
    
    /**
     * 构建情节连贯性检查提示词
     */
    public List<Message> buildContinuityCheckPrompt(String previousContent, String currentContent) {
        String systemPrompt = """
            你是一位专业的小说编辑，擅长检查故事的连贯性和逻辑性。
            
            请检查前后文本之间的连贯性，重点关注：
            1. **情节逻辑**：事件发展是否合理
            2. **人物一致性**：角色行为是否符合设定
            3. **时空连续性**：时间和空间的转换是否自然
            4. **语言风格**：前后文风格是否统一
            5. **情感基调**：情感变化是否合理
            
            如果发现问题，请提出具体的修改建议。
            """;
        
        String userPrompt = String.format("""
            请检查以下前后文本的连贯性：
            
            ## 前文内容
            %s
            
            ## 当前内容
            %s
            
            请分析连贯性并提出改进建议。
            """, previousContent, currentContent);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));
        
        return messages;
    }
} 