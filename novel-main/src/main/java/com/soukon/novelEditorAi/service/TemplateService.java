package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.model.template.TemplateListDTO;
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
     * 根据ID获取模板详情（包含content字段）
     * @param id 模板ID
     * @return 模板信息
     */
    Result<Template> getTemplateById(Long id);

    /**
     * 分页查询模板（已废弃，建议使用pageTemplateList）
     * @param page 页码
     * @param size 每页大小
     * @param name 模板名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     * @deprecated 建议使用 pageTemplateList 方法，避免返回大的content字段
     */
    @Deprecated
    Result<Page<Template>> pageTemplates(int page, int size, String name, String tag);

    /**
     * 分页查询模板列表（不包含content字段）
     * @param page 页码
     * @param size 每页大小
     * @param name 模板名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     */
    Result<Page<TemplateListDTO>> pageTemplateList(int page, int size, String name, String tag);

    /**
     * 根据标签查询模板（已废弃，建议使用getTemplateListByTag）
     * @param tag 标签
     * @return 模板列表
     * @deprecated 建议使用 getTemplateListByTag 方法，避免返回大的content字段
     */
    @Deprecated
    Result<List<Template>> getTemplatesByTag(String tag);

    /**
     * 根据标签查询模板列表（不包含content字段）
     * @param tag 标签
     * @return 模板列表
     */
    Result<List<TemplateListDTO>> getTemplateListByTag(String tag);

    /**
     * 创建模板并自动触发向量化
     * @param template 模板信息
     * @param autoIndex 是否自动进行向量化
     * @return 创建结果
     */
    Result<Template> createTemplateWithAutoIndex(Template template, boolean autoIndex);

    /**
     * 通过文件上传创建模板并自动触发向量化
     * @param request 上传请求
     * @param autoIndex 是否自动进行向量化
     * @return 创建结果
     */
    Result<Template> createTemplateWithFileAndAutoIndex(TemplateUploadRequest request, boolean autoIndex);
} 