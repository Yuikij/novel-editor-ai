package com.soukon.novelEditorAi.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.service.ChapterContentService;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.service.CharacterService;
import com.soukon.novelEditorAi.service.RagService;
import com.soukon.novelEditorAi.service.ConsistentVectorSearchService;
import com.soukon.novelEditorAi.service.impl.ChapterContentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

    @Autowired
    private RagService ragService;


    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ConsistentVectorSearchService consistentVectorSearchService;


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
        if (name == null || name.isEmpty()) {
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


    @Tool(name = "rag_query", description = """
            通过关键字或者提问句，检索任何小说中的相关信息，包括章节、角色、世界观，小说内容等信息
            """)
    public String ragQuery(@ToolParam(description = "章节id") String projectId,
                           @ToolParam(description = "想要查询的内容关键词") String query,
                           @ToolParam(description = "计划id") String planId) {
        log.info("=== 工具调用开始 ===");
        log.info("工具名称: rag_query");
        log.info("调用参数: 章节ID={}, 查询内容={}, 计划ID={}", projectId, query, planId);

        // 安全地获取PlanContext，避免测试环境中的null错误
        PlanContext planContext = chapterContentService.getPlanContextMap().get(planId);
        if (planContext != null) {
            planContext.setMessage("正在检索相关信息：" + query);
        } else {
            log.warn("测试环境：未找到planId={}对应的PlanContext", planId);
        }

        try {
            // 使用一致性RAG服务检索相关信息
            SearchRequest request = SearchRequest.builder()
                    .filterExpression("projectId == '" + projectId + "'")
                    .topK(8).query(query).build();
            List<Document> relevantDocs = consistentVectorSearchService.searchWithConsistency(request, Long.parseLong(projectId));
            if (relevantDocs == null || relevantDocs.isEmpty()) {
                log.info("没有找到与项目 {} 相关的文档", projectId);
                return "未找到相关背景信息";
            }
            // 格式化检索结果
            StringBuilder result = new StringBuilder();
            result.append("检索到的相关信息：\n\n");
            for (int i = 0; i < relevantDocs.size(); i++) {
                Document doc = relevantDocs.get(i);
                String content = doc.getText();
                result.append(i + 1).append(". ").append("\n");
                result.append(content).append("\n\n");
            }
            String finalResult = result.toString();
            log.info("RAG查询结果: {}", finalResult.length() > 100 ? finalResult.substring(0, 100) + "..." : finalResult);
            log.info("=== 工具调用结束 ===");
            return finalResult;
        } catch (Exception e) {
            log.error("RAG查询失败: {}", e.getMessage(), e);
            log.info("=== 工具调用结束（失败）===");
            return "RAG查询失败: " + e.getMessage();
        }
    }

//    @Tool(name = "weather_get", description = """
//           获取今天天气
//           """)
//    public String getWeather(){
//        log.info("获取今天天气");
//        return "晴天，温度25度";
//    }
}
