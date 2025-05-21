package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.model.template.TemplateUploadRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 模板服务接口
 */
public interface TemplateService {

    /**
     * 创建模板
     * @param template 模板信息
     * @return 创建结果
     */
    Result<Template> createTemplate(Template template);

    /**
     * 通过文件上传创建模板
     * @param request 上传请求
     * @return 创建结果
     */
    Result<Template> createTemplateWithFile(TemplateUploadRequest request);

    /**
     * 批量创建模板
     * @param templates 模板列表
     * @return 创建结果
     */
    Result<List<Template>> batchCreateTemplates(List<Template> templates);

    /**
     * 更新模板
     * @param template 模板信息
     * @return 更新结果
     */
    Result<Boolean> updateTemplate(Template template);

    /**
     * 通过文件上传更新模板
     * @param request 上传请求
     * @return 更新结果
     */
    Result<Boolean> updateTemplateWithFile(TemplateUploadRequest request);

    /**
     * 删除模板
     * @param id 模板ID
     * @return 删除结果
     */
    Result<Boolean> deleteTemplate(Long id);

    /**
     * 批量删除模板
     * @param ids 模板ID列表
     * @return 删除结果
     */
    Result<Boolean> batchDeleteTemplates(List<Long> ids);

    /**
     * 根据ID获取模板
     * @param id 模板ID
     * @return 模板信息
     */
    Result<Template> getTemplateById(Long id);

    /**
     * 分页查询模板
     * @param page 页码
     * @param size 每页大小
     * @param name 模板名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     */
    Result<Page<Template>> pageTemplates(int page, int size, String name, String tag);

    /**
     * 根据标签查询模板
     * @param tag 标签
     * @return 模板列表
     */
    Result<List<Template>> getTemplatesByTag(String tag);
} 