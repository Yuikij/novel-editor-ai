package com.soukon.novelEditorAi.controller;

import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.model.chat.ProjectChatRequest;
import com.soukon.novelEditorAi.model.chat.ProjectChatContext;
import com.soukon.novelEditorAi.service.ProjectChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;



/**
 * 项目流式对话控制器
 */
@RestController
@RequestMapping("/projects/{projectId}/chat")
@Slf4j
public class ProjectChatController {

    @Autowired
    private ProjectChatService projectChatService;

    /**
     * 项目流式对话
     * @param projectId 项目ID
     * @param request 对话请求
     * @return 流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> streamChat(@PathVariable("projectId") Long projectId,
                                   @RequestBody ProjectChatRequest request) {
        log.info("开始项目流式对话: projectId={}, message={}", projectId, request.getMessage());
        
        try {
            return projectChatService.streamChat(projectId, request)
                    .doOnComplete(() -> log.info("项目流式对话完成: projectId={}", projectId))
                    .doOnError(error -> log.error("项目流式对话失败: projectId={}", projectId, error));
        } catch (Exception e) {
            log.error("项目流式对话启动失败: projectId={}", projectId, e);
            return Flux.just("抱歉，对话服务暂时不可用，请稍后重试。");
        }
    }

    /**
     * 获取项目对话上下文
     * @param projectId 项目ID
     * @param sessionId 会话ID（可选）
     * @return 对话上下文
     */
    @GetMapping("/context")
    public Result<ProjectChatContext> getChatContext(@PathVariable("projectId") Long projectId,
                                                     @RequestParam(required = false) String sessionId) {
        try {
            ProjectChatContext context = projectChatService.getChatContext(projectId, sessionId);
            return Result.success("获取对话上下文成功", context);
        } catch (Exception e) {
            log.error("获取项目对话上下文失败: projectId={}, sessionId={}", projectId, sessionId, e);
            return Result.error("获取对话上下文失败: " + e.getMessage());
        }
    }

    /**
     * 清除项目对话上下文
     * @param projectId 项目ID
     * @param sessionId 会话ID（可选）
     * @return 清除结果
     */
    @DeleteMapping("/context")
    public Result<Void> clearChatContext(@PathVariable("projectId") Long projectId,
                                         @RequestParam(required = false) String sessionId) {
        try {
            projectChatService.clearChatContext(projectId, sessionId);
            return Result.success("清除对话上下文成功", null);
        } catch (Exception e) {
            log.error("清除项目对话上下文失败: projectId={}, sessionId={}", projectId, sessionId, e);
            return Result.error("清除对话上下文失败: " + e.getMessage());
        }
    }

    /**
     * 获取项目对话历史
     * @param projectId 项目ID
     * @param sessionId 会话ID（可选）
     * @param limit 限制数量
     * @return 对话历史
     */
    @GetMapping("/history")
    public Result<ProjectChatContext> getChatHistory(@PathVariable("projectId") Long projectId,
                                                     @RequestParam(required = false) String sessionId,
                                                     @RequestParam(defaultValue = "20") Integer limit) {
        try {
            ProjectChatContext history = projectChatService.getChatHistory(projectId, sessionId, limit);
            return Result.success("获取对话历史成功", history);
        } catch (Exception e) {
            log.error("获取项目对话历史失败: projectId={}, sessionId={}", projectId, sessionId, e);
            return Result.error("获取对话历史失败: " + e.getMessage());
        }
    }
} 