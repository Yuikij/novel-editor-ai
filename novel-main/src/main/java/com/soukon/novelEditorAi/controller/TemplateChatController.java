package com.soukon.novelEditorAi.controller;

import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.model.template.TemplateChatRequest;
import com.soukon.novelEditorAi.service.TemplateChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 模板对话控制器
 */
@RestController
@RequestMapping("/templates/chat")
@Slf4j
public class TemplateChatController {

    @Autowired
    private TemplateChatService templateChatService;

    /**
     * 与模板进行对话
     * @param request 对话请求
     * @return 对话响应
     */
    @PostMapping
    public Result<String> chatWithTemplate(@RequestBody TemplateChatRequest request) {
        log.info("模板对话请求, templateId: {}, message: {}", request.getTemplateId(), request.getMessage());
        return templateChatService.chatWithTemplate(request);
    }

    /**
     * 与模板进行流式对话
     * @param request 对话请求
     * @return 流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithTemplateStream(@RequestBody TemplateChatRequest request) {
        log.info("模板流式对话请求, templateId: {}, message: {}", request.getTemplateId(), request.getMessage());
        return templateChatService.chatWithTemplateStream(request);
    }

    /**
     * 检查模板是否可以对话
     * @param id 模板ID
     * @return 是否可以对话
     */
    @GetMapping("/{id}/can-chat")
    public Result<Boolean> canChatWithTemplate(@PathVariable(name = "id") Long id) {
        log.info("检查模板对话状态请求, ID: {}", id);
        return templateChatService.canChatWithTemplate(id);
    }

    /**
     * 简化的对话接口（通过URL参数）
     * @param id 模板ID
     * @param message 消息内容
     * @param conversationId 对话ID（可选）
     * @param maxResults 最大检索结果数（可选）
     * @param similarityThreshold 相似度阈值（可选）
     * @return 对话响应
     */
    @GetMapping("/{id}")
    public Result<String> chatWithTemplateSimple(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "message") String message,
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @RequestParam(name = "maxResults", required = false) Integer maxResults,
            @RequestParam(name = "similarityThreshold", required = false) Float similarityThreshold) {
        
        log.info("简化模板对话请求, templateId: {}, message: {}", id, message);
        
        TemplateChatRequest request = TemplateChatRequest.builder()
                .templateId(id)
                .message(message)
                .conversationId(conversationId)
                .maxResults(maxResults)
                .similarityThreshold(similarityThreshold)
                .build();
        
        return templateChatService.chatWithTemplate(request);
    }

    /**
     * 简化的流式对话接口（通过URL参数）
     * @param id 模板ID
     * @param message 消息内容
     * @param conversationId 对话ID（可选）
     * @param maxResults 最大检索结果数（可选）
     * @param similarityThreshold 相似度阈值（可选）
     * @return 流式响应
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithTemplateStreamSimple(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "message") String message,
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @RequestParam(name = "maxResults", required = false) Integer maxResults,
            @RequestParam(name = "similarityThreshold", required = false) Float similarityThreshold) {
        
        log.info("简化模板流式对话请求, templateId: {}, message: {}", id, message);
        
        TemplateChatRequest request = TemplateChatRequest.builder()
                .templateId(id)
                .message(message)
                .conversationId(conversationId)
                .maxResults(maxResults)
                .similarityThreshold(similarityThreshold)
                .build();
        
        return templateChatService.chatWithTemplateStream(request);
    }
} 