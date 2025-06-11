package com.soukon.novelEditorAi.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.service.ChapterContentService;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.service.CharacterService;
import com.soukon.novelEditorAi.service.impl.ChapterContentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ToolService {

//    获取当前未完成情节
//    完成当前情节
//    根据角色名称查找角色详情
//    根据关键字检索全文相关内容

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private CharacterService characterService;

    @Autowired
    private ChapterContentService chapterContentService;

    @Tool(name = "get_character_info", description = """
            根据角色名称获取角色相关信息。
            """)
    public String getCharacterInfo(@ToolParam(description = "章节id") String chapterId,
                                   @ToolParam(description = "项目id") String projectId,
                                   @ToolParam(description = "角色名称") String name,
                                   @ToolParam(description = "计划id") String planId) {
        log.info("=== 工具调用开始 ===");
        log.info("工具名称: get_character_info");
        log.info("调用参数: 章节ID={}, 项目ID={}, 角色名称={}, 计划ID={}", chapterId, projectId, name, planId);
        
        LambdaQueryWrapper<Character> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            return "";
        }
        queryWrapper.eq(Character::getProjectId, Long.parseLong(projectId))
                .eq(Character::getName, name);
        String prompt = "";
        List<Character> list = characterService.list(queryWrapper);
        
        // 安全地获取PlanContext，避免测试环境中的null错误
        PlanContext planContext = chapterContentService.getPlanContextMap().get(planId);
        if (planContext != null) {
            planContext.setMessage("正在获取角色相关信息：" + name);
        } else {
            log.warn("测试环境：未找到planId={}对应的PlanContext", planId);
        }
        
        log.info("工具调用：获取角色信息，章节ID: {}, 项目ID: {}, 角色名称: {}, 计划ID: {}", chapterId, projectId, name, planId);
        if (!list.isEmpty()) {
            prompt = characterService.toPrompt(list.get(0));
        }
        
        log.info("工具调用结果: {}", prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt);
        log.info("=== 工具调用结束 ===");
        return prompt;
    }


    @Tool(name = "latest_content_get", description = """
            获取目前小说的最新内容，如果该章节有内容，则返回最新的内容，如果没有内容，则返回上一章节内容。
            """)
    public String getLatestChapterContent(@ToolParam(description = "章节id") String chapterId,
                                          @ToolParam(description = "想要获取的字数") Integer wordCount,
                                          @ToolParam(description = "计划id") String planId) {
        log.info("=== 工具调用开始 ===");
        log.info("工具名称: latest_content_get");
        log.info("调用参数: 章节ID={}, 字数={}, 计划ID={}", chapterId, wordCount, planId);
        
        // 安全地获取PlanContext，避免测试环境中的null错误
        PlanContext planContext = chapterContentService.getPlanContextMap().get(planId);
        if (planContext != null) {
            planContext.setMessage("正在获取最新内容，" + wordCount + "字");
        } else {
            log.warn("测试环境：未找到planId={}对应的PlanContext", planId);
        }
        
        log.info("工具调用：获取最新内容，章节ID: {}, 字数: {}, 计划ID: {}", chapterId, wordCount, planId);
        String result = chapterService.getLatestChapterContent(Long.parseLong(chapterId), wordCount);
        
        log.info("工具调用结果: {}", result.length() > 100 ? result.substring(0, 100) + "..." : result);
        log.info("=== 工具调用结束 ===");
        return result;
    }

//    @Tool(name = "weather_get", description = """
//           获取今天天气
//           """)
//    public String getWeather(){
//        log.info("获取今天天气");
//        return "晴天，温度25度";
//    }
}
