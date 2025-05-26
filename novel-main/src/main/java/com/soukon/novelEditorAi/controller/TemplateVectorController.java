package com.soukon.novelEditorAi.controller;

import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.model.template.TemplateRequest;
import com.soukon.novelEditorAi.model.template.TemplateVectorProgressDTO;
import com.soukon.novelEditorAi.service.TemplateVectorService;
import com.soukon.novelEditorAi.utils.VectorStoreDebugUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 模板向量化控制器
 */
@RestController
@RequestMapping("/templates/vector")
@Slf4j
public class TemplateVectorController {

    @Autowired
    private TemplateVectorService templateVectorService;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 查询模板向量化进度
     * @param id 模板ID
     * @return 向量化进度信息
     */
    @GetMapping("/{id}/progress")
    public Result<TemplateVectorProgressDTO> getVectorProgress(@PathVariable(name = "id") Long id) {
        log.info("查询模板向量化进度请求, ID: {}", id);
        return templateVectorService.getVectorProgress(id);
    }

    /**
     * 流式获取模板向量化进度
     * @param id 模板ID
     * @return 进度流
     */
    @GetMapping(value = "/{id}/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TemplateVectorProgressDTO> getVectorProgressStream(@PathVariable(name = "id") Long id) {
        log.info("流式查询模板向量化进度请求, ID: {}", id);
        return templateVectorService.getVectorProgressStream(id);
    }

    /**
     * 手动导入模板到向量数据库（覆盖模式）
     * @param id 模板ID
     * @return 导入结果
     */
    @PostMapping("/{id}/index")
    public Result<Boolean> indexTemplate(@PathVariable(name = "id") Long id) {
        log.info("手动导入模板到向量数据库请求, ID: {}", id);
        return templateVectorService.indexTemplate(id);
    }

    /**
     * 异步导入模板到向量数据库
     * @param id 模板ID
     * @return 导入结果
     */
    @PostMapping("/{id}/index/async")
    public Result<Boolean> indexTemplateAsync(@PathVariable(name = "id") Long id) {
        log.info("异步导入模板到向量数据库请求, ID: {}", id);
        return templateVectorService.indexTemplateAsync(id);
    }

    /**
     * 删除模板的向量索引
     * @param id 模板ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}/index")
    public Result<Boolean> deleteTemplateIndex(@PathVariable(name = "id") Long id) {
        log.info("删除模板向量索引请求, ID: {}", id);
        return templateVectorService.deleteTemplateIndex(id);
    }

    /**
     * 批量导入模板到向量数据库
     * @param request 包含模板ID列表的请求对象
     * @return 导入结果
     */
    @PostMapping("/batch/index")
    public Result<Boolean> batchIndexTemplates(@RequestBody TemplateRequest request) {
        log.info("批量导入模板到向量数据库请求, IDs: {}", request.getIds());
        return templateVectorService.batchIndexTemplates(request.getIds());
    }

    /**
     * 调试模板向量存储
     * @param id 模板ID
     * @return 调试信息
     */
    @GetMapping("/{id}/debug")
    public Result<String> debugTemplateVectorStore(@PathVariable(name = "id") Long id) {
        log.info("调试模板向量存储请求, ID: {}", id);
        try {
            VectorStoreDebugUtil.debugTemplateDocuments(vectorStore, id);
            return Result.success("调试完成，请查看日志", "调试信息已输出到日志");
        } catch (Exception e) {
            log.error("调试模板向量存储失败: {}", e.getMessage(), e);
            return Result.error("调试失败: " + e.getMessage());
        }
    }
} 