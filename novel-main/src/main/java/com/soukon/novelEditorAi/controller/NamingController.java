package com.soukon.novelEditorAi.controller;

import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.model.naming.NamingResponse;
import com.soukon.novelEditorAi.service.NamingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能取名服务
 * 提供小说名称、角色名称、章节名称等智能取名功能
 */
@RestController
@RequestMapping("/naming")
public class NamingController {

    private final NamingService namingService;

    public NamingController(NamingService namingService) {
        this.namingService = namingService;
    }

    /**
     * 小说名称生成接口
     * @param genre 小说类型
     * @param style 写作风格
     * @param tags 标签
     * @param synopsis 简介
     * @param targetAudience 目标受众
     * @param writingRequirements 写作要求
     * @return 生成的名称列表
     */
    @GetMapping("/novel")
    public Result<NamingResponse> generateNovelName(
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "style", required = false) String style,
            @RequestParam(value = "tags", required = false) String[] tags,
            @RequestParam(value = "synopsis", required = false) String synopsis,
            @RequestParam(value = "targetAudience", required = false) String targetAudience,
            @RequestParam(value = "writingRequirements", required = false) String[] writingRequirements) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("类型", genre != null ? genre : "未指定");
        parameters.put("风格", style != null ? style : "未指定");
        parameters.put("标签", tags != null ? tags : new String[]{"未指定"});
        parameters.put("简介", synopsis != null ? synopsis : "未指定");
        parameters.put("目标受众", targetAudience != null ? targetAudience : "未指定");
        parameters.put("写作要求", writingRequirements != null ? writingRequirements : new String[]{"未指定"});
        parameters.put("需求", "小说名称");

        NamingResponse response = namingService.generateNames(parameters);
        return Result.success(response);
    }

    /**
     * 角色名称生成接口
     * @param role 角色定位
     * @param personality 性格特点
     * @param background 背景故事
     * @return 生成的名称列表
     */
    @GetMapping("/character")
    public Result<NamingResponse> generateCharacterName(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "personality", required = false) String personality,
            @RequestParam(value = "background", required = false) String background) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("角色定位", role != null ? role : "未指定");
        parameters.put("性格特点", personality != null ? personality : "未指定");
        parameters.put("背景故事", background != null ? background : "未指定");
        parameters.put("需求", "角色名称");

        NamingResponse response = namingService.generateNames(parameters);
        return Result.success(response);
    }

    /**
     * 章节名称生成接口
     * @param novelTitle 小说名称
     * @param chapterSummary 章节摘要
     * @param chapterIndex 章节序号
     * @return 生成的名称列表
     */
    @GetMapping("/chapter")
    public Result<NamingResponse> generateChapterName(
            @RequestParam(value = "novelTitle", required = false) String novelTitle,
            @RequestParam(value = "chapterSummary", required = false) String chapterSummary,
            @RequestParam(value = "chapterIndex", required = false) Integer chapterIndex) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("小说名称", novelTitle != null ? novelTitle : "未指定");
        parameters.put("章节摘要", chapterSummary != null ? chapterSummary : "未指定");
        parameters.put("章节序号", chapterIndex != null ? chapterIndex : "未指定");
        parameters.put("需求", "章节名称");

        NamingResponse response = namingService.generateNames(parameters);
        return Result.success(response);
    }

    /**
     * 流式取名接口
     * @param queryType 查询类型(novel/character/chapter)
     * @param description 需求描述
     * @param response HTTP响应对象
     * @return 流式响应
     */
    @GetMapping("/stream/{queryType}")
    public Flux<String> streamGenerateNames(
            @PathVariable("queryType") String queryType,
            @RequestParam("description") String description,
            HttpServletResponse response) {

        response.setCharacterEncoding("UTF-8");
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("类型", queryType);
        parameters.put("描述", description);
        parameters.put("需求", queryType.equals("novel") ? "小说名称" : 
                            queryType.equals("character") ? "角色名称" : "章节名称");

        return namingService.generateNamesStream(parameters);
    }
} 