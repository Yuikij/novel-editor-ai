package com.soukon.novelEditorAi.agent.tool;

import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.service.ChapterContentService;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.service.impl.ChapterContentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ToolService {

//    获取当前未完成情节
//    完成当前情节
//    查找角色
//    根据关键字检索全文相关内容

    @Autowired
    private ChapterService chapterService;

    private ChapterContentService chapterContentService;

    @Tool(name = "latest_content_get", description = """
           获取目前小说的最新内容，如果该章节有内容，则返回最新的内容，如果没有内容，则返回上一章节内容。
           """)
    public String getLatestChapterContent(@ToolParam(description = "章节id")Long chapterId,
                                          @ToolParam(description = "想要获取的字数")Integer wordCount,
                                          @ToolParam(description = "计划id")String planId) {
        ConcurrentHashMap<String, PlanContext> planContextMap = chapterContentService.getPlanContextMap();
        PlanContext planContext = planContextMap.get(planId);
        planContext.setMessage("正在获取最新内容，" + wordCount + "字");
        log.info("获取最新内容，章节ID: {}, 字数: {}, 计划ID: {}", chapterId, wordCount, planId);
        return chapterService.getLatestChapterContent(chapterId, wordCount);
    }

//    @Tool(name = "weather_get", description = """
//           获取今天天气
//           """)
//    public String getWeather(){
//        log.info("获取今天天气");
//        return "晴天，温度25度";
//    }
}
