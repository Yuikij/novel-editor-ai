package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.model.template.TemplateListDTO;
import com.soukon.novelEditorAi.model.template.TemplateRequest;
import com.soukon.novelEditorAi.model.template.TemplateUploadRequest;
import com.soukon.novelEditorAi.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 模板管理控制器
 */
@RestController
@RequestMapping("/templates")
@Slf4j
public class TemplateController {

    @Autowired
    private TemplateService templateService;

    /**
     * 创建模板
     * @param template 模板信息
     * @return 创建结果
     */
    @PostMapping
    public Result<Template> createTemplate(@RequestBody Template template) {
        log.info("创建模板请求: {}", template);
        return templateService.createTemplate(template);
    }

    /**
     * 通过文件上传创建模板
     * @param name 模板名称
     * @param tags 模板标签
     * @param file 模板文件（文件和content至少提供一个）
     * @param content 模板内容文本（文件优先）
     * @return 创建结果
     */
    @PostMapping("/upload")
    public Result<Template> createTemplateWithFile(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "content", required = false) String content) {
        log.info("创建模板请求, name: {}, tags: {}, hasFile: {}, hasContent: {}", 
                name, tags, (file != null && !file.isEmpty()), StringUtils.hasText(content));
        
        TemplateUploadRequest request = TemplateUploadRequest.builder()
                .name(name)
                .tags(tags)
                .file(file)
                .content(content)
                .build();
        
        return templateService.createTemplateWithFile(request);
    }

    /**
     * 批量创建模板
     * @param templates 模板列表
     * @return 创建结果
     */
    @PostMapping("/batch")
    public Result<List<Template>> batchCreateTemplates(@RequestBody List<Template> templates) {
        log.info("批量创建模板请求, 数量: {}", templates.size());
        return templateService.batchCreateTemplates(templates);
    }

    /**
     * 更新模板
     * @param template 模板信息
     * @return 更新结果
     */
    @PutMapping
    public Result<Boolean> updateTemplate(@RequestBody Template template) {
        log.info("更新模板请求: {}", template);
        return templateService.updateTemplate(template);
    }

    /**
     * 通过文件上传更新模板
     * @param id 模板ID
     * @param name 模板名称（可选）
     * @param tags 模板标签（可选）
     * @param file 模板文件（可选，优先级高）
     * @param content 模板内容文本（可选，文件优先）
     * @return 更新结果
     */
    @PutMapping("/upload")
    public Result<Boolean> updateTemplateWithFile(
            @RequestParam(name = "id") Long id,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "content", required = false) String content) {
        log.info("更新模板请求, id: {}, name: {}, tags: {}, hasFile: {}, hasContent: {}", 
                id, name, tags, (file != null && !file.isEmpty()), StringUtils.hasText(content));
        
        TemplateUploadRequest request = TemplateUploadRequest.builder()
                .id(id)
                .name(name)
                .tags(tags)
                .file(file)
                .content(content)
                .build();
        
        return templateService.updateTemplateWithFile(request);
    }

    /**
     * 删除模板
     * @param id 模板ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteTemplate(@PathVariable(name = "id") Long id) {
        log.info("删除模板请求, ID: {}", id);
        return templateService.deleteTemplate(id);
    }

    /**
     * 批量删除模板
     * @param request 包含ID列表的请求对象
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<Boolean> batchDeleteTemplates(@RequestBody TemplateRequest request) {
        log.info("批量删除模板请求, IDs: {}", request.getIds());
        return templateService.batchDeleteTemplates(request.getIds());
    }

    /**
     * 根据ID获取模板详情（包含完整的content字段）
     * @param id 模板ID
     * @return 模板详情信息
     */
    @GetMapping("/{id}/detail")
    public Result<Template> getTemplateDetail(@PathVariable(name = "id") Long id) {
        log.info("查询模板详情请求, ID: {}", id);
        return templateService.getTemplateById(id);
    }

    /**
     * 根据ID获取模板基本信息（兼容旧接口，建议使用detail接口获取完整信息）
     * @param id 模板ID
     * @return 模板信息
     */
    @GetMapping("/{id}")
    public Result<Template> getTemplateById(@PathVariable(name = "id") Long id) {
        log.info("查询模板请求, ID: {}", id);
        return templateService.getTemplateById(id);
    }

    /**
     * 分页查询模板列表（不包含content字段，推荐使用）
     * @param page 页码
     * @param size 每页大小
     * @param name 模板名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<Page<TemplateListDTO>> pageTemplateList(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "tag", required = false) String tag) {
        log.info("分页查询模板列表请求, page: {}, size: {}, name: {}, tag: {}", page, size, name, tag);
        return templateService.pageTemplateList(page, size, name, tag);
    }

    /**
     * 分页查询模板（包含content字段，不推荐在列表场景使用）
     * @param page 页码
     * @param size 每页大小
     * @param name 模板名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     * @deprecated 建议使用 /list 接口，避免返回大的content字段
     */
    @GetMapping("/page")
    @Deprecated
    public Result<Page<Template>> pageTemplates(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "tag", required = false) String tag) {
        log.info("分页查询模板请求（已废弃）, page: {}, size: {}, name: {}, tag: {}", page, size, name, tag);
        return templateService.pageTemplates(page, size, name, tag);
    }

    /**
     * 分页查询模板（使用请求体，推荐使用）
     * @param request 请求对象
     * @return 分页结果
     */
    @PostMapping("/search")
    public Result<Page<TemplateListDTO>> searchTemplateList(@RequestBody TemplateRequest request) {
        log.info("高级查询模板列表请求: {}", request);
        Integer page = request.getPage() != null ? request.getPage() : 1;
        Integer size = request.getSize() != null ? request.getSize() : 10;
        return templateService.pageTemplateList(page, size, request.getName(), request.getTags());
    }

    /**
     * 分页查询模板（使用请求体，包含content字段，不推荐）
     * @param request 请求对象
     * @return 分页结果
     * @deprecated 建议使用 /search 接口，避免返回大的content字段
     */
    @PostMapping("/search-full")
    @Deprecated
    public Result<Page<Template>> searchTemplates(@RequestBody TemplateRequest request) {
        log.info("高级查询模板请求（已废弃）: {}", request);
        Integer page = request.getPage() != null ? request.getPage() : 1;
        Integer size = request.getSize() != null ? request.getSize() : 10;
        return templateService.pageTemplates(page, size, request.getName(), request.getTags());
    }

    /**
     * 根据标签查询模板列表（不包含content字段，推荐使用）
     * @param tag 标签
     * @return 模板列表
     */
    @GetMapping("/tag/{tag}/list")
    public Result<List<TemplateListDTO>> getTemplateListByTag(@PathVariable(name = "tag") String tag) {
        log.info("根据标签查询模板列表请求, tag: {}", tag);
        return templateService.getTemplateListByTag(tag);
    }

    /**
     * 根据标签查询模板（包含content字段，不推荐）
     * @param tag 标签
     * @return 模板列表
     * @deprecated 建议使用 /tag/{tag}/list 接口，避免返回大的content字段
     */
    @GetMapping("/tag/{tag}")
    @Deprecated
    public Result<List<Template>> getTemplatesByTag(@PathVariable(name = "tag") String tag) {
        log.info("根据标签查询模板请求（已废弃）, tag: {}", tag);
        return templateService.getTemplatesByTag(tag);
    }
} 