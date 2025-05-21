package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.mapper.TemplateMapper;
import com.soukon.novelEditorAi.model.template.TemplateUploadRequest;
import com.soukon.novelEditorAi.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模板服务实现类
 */
@Service
@Slf4j
public class TemplateServiceImpl implements TemplateService {

    @Autowired
    private TemplateMapper templateMapper;

    @Override
    public Result<Template> createTemplate(Template template) {
        try {
            // 名称不能为空
            if (!StringUtils.hasText(template.getName())) {
                return Result.error("模板名称不能为空");
            }
            
            templateMapper.insert(template);
            log.info("创建模板成功: {}", template.getId());
            return Result.success("创建成功", template);
        } catch (Exception e) {
            log.error("创建模板失败: {}", e.getMessage(), e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Template> createTemplateWithFile(TemplateUploadRequest request) {
        try {
            // 名称不能为空
            if (!StringUtils.hasText(request.getName())) {
                return Result.error("模板名称不能为空");
            }
            
            // 获取内容 - 文件优先，其次是文本内容
            String content = null;
            
            // 如果有文件，优先读取文件内容
            if (request.getFile() != null && !request.getFile().isEmpty()) {
                content = readFile(request.getFile());
                log.info("从文件读取模板内容，文件名: {}", request.getFile().getOriginalFilename());
            } 
            // 如果没有文件但有文本内容
            else if (StringUtils.hasText(request.getContent())) {
                content = request.getContent();
                log.info("使用提供的文本内容创建模板");
            } 
            // 如果既没有文件也没有文本内容
            else {
                return Result.error("模板内容不能为空，请提供文件或文本内容");
            }
            
            // 创建模板
            Template template = Template.builder()
                    .name(request.getName())
                    .tags(request.getTags())
                    .content(content)
                    .build();
            
            templateMapper.insert(template);
            log.info("创建模板成功: {}", template.getId());
            return Result.success("创建成功", template);
        } catch (Exception e) {
            log.error("创建模板失败: {}", e.getMessage(), e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<List<Template>> batchCreateTemplates(List<Template> templates) {
        try {
            if (templates == null || templates.isEmpty()) {
                return Result.error("模板列表不能为空");
            }
            
            List<Template> createdTemplates = new ArrayList<>();
            
            // 批量创建模板
            for (Template template : templates) {
                // 名称不能为空
                if (!StringUtils.hasText(template.getName())) {
                    continue; // 跳过名称为空的模板
                }
                
                templateMapper.insert(template);
                createdTemplates.add(template);
            }
            
            if (createdTemplates.isEmpty()) {
                return Result.error("没有有效的模板可创建");
            }
            
            log.info("批量创建模板成功，数量: {}", createdTemplates.size());
            return Result.success("批量创建成功", createdTemplates);
        } catch (Exception e) {
            log.error("批量创建模板失败: {}", e.getMessage(), e);
            return Result.error("批量创建失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> updateTemplate(Template template) {
        try {
            // ID不能为空
            if (template.getId() == null) {
                return Result.error("模板ID不能为空");
            }
            
            // 检查模板是否存在
            Template existingTemplate = templateMapper.selectById(template.getId());
            if (existingTemplate == null) {
                return Result.error("模板不存在");
            }
            
            // 更新模板
            templateMapper.updateById(template);
            log.info("更新模板成功: {}", template.getId());
            return Result.success("更新成功", true);
        } catch (Exception e) {
            log.error("更新模板失败: {}", e.getMessage(), e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> updateTemplateWithFile(TemplateUploadRequest request) {
        try {
            // ID不能为空
            if (request.getId() == null) {
                return Result.error("模板ID不能为空");
            }
            
            // 检查模板是否存在
            Template existingTemplate = templateMapper.selectById(request.getId());
            if (existingTemplate == null) {
                return Result.error("模板不存在");
            }
            
            // 获取内容 - 文件优先，其次是文本内容，如果都没有则保留原有内容
            String content = null;
            
            // 如果有文件，优先读取文件内容
            if (request.getFile() != null && !request.getFile().isEmpty()) {
                content = readFile(request.getFile());
                log.info("从文件读取模板内容，文件名: {}", request.getFile().getOriginalFilename());
            } 
            // 如果没有文件但有文本内容
            else if (StringUtils.hasText(request.getContent())) {
                content = request.getContent();
                log.info("使用提供的文本内容更新模板");
            } 
            // 如果既没有文件也没有文本内容，保持原有内容不变
            else {
                content = existingTemplate.getContent();
                log.info("保持原有内容不变");
            }
            
            // 更新模板
            Template template = Template.builder()
                    .id(request.getId())
                    .name(StringUtils.hasText(request.getName()) ? request.getName() : existingTemplate.getName())
                    .tags(StringUtils.hasText(request.getTags()) ? request.getTags() : existingTemplate.getTags())
                    .content(content)
                    .build();
            
            templateMapper.updateById(template);
            log.info("更新模板成功: {}", template.getId());
            return Result.success("更新成功", true);
        } catch (Exception e) {
            log.error("更新模板失败: {}", e.getMessage(), e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> deleteTemplate(Long id) {
        try {
            // 检查模板是否存在
            Template existingTemplate = templateMapper.selectById(id);
            if (existingTemplate == null) {
                return Result.error("模板不存在");
            }
            
            // 删除模板
            templateMapper.deleteById(id);
            log.info("删除模板成功: {}", id);
            return Result.success("删除成功", true);
        } catch (Exception e) {
            log.error("删除模板失败: {}", e.getMessage(), e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Boolean> batchDeleteTemplates(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("模板ID列表不能为空");
            }
            
            // 批量删除模板
            for (Long id : ids) {
                templateMapper.deleteById(id);
            }
            
            log.info("批量删除模板成功，数量: {}", ids.size());
            return Result.success("批量删除成功", true);
        } catch (Exception e) {
            log.error("批量删除模板失败: {}", e.getMessage(), e);
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Template> getTemplateById(Long id) {
        try {
            Template template = templateMapper.selectById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            
            return Result.success("查询成功", template);
        } catch (Exception e) {
            log.error("查询模板失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Page<Template>> pageTemplates(int page, int size, String name, String tag) {
        try {
            // 分页参数
            Page<Template> pageParam = new Page<>(page, size);
            
            // 构建查询条件
            LambdaQueryWrapper<Template> queryWrapper = new LambdaQueryWrapper<>();
            
            // 根据名称模糊查询
            if (StringUtils.hasText(name)) {
                queryWrapper.like(Template::getName, name);
            }
            
            // 根据标签模糊查询
            if (StringUtils.hasText(tag)) {
                queryWrapper.like(Template::getTags, tag);
            }
            
            // 执行分页查询
            Page<Template> resultPage = templateMapper.selectPage(pageParam, queryWrapper);
            
            return Result.success("查询成功", resultPage);
        } catch (Exception e) {
            log.error("分页查询模板失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<Template>> getTemplatesByTag(String tag) {
        try {
            if (!StringUtils.hasText(tag)) {
                return Result.error("标签不能为空");
            }
            
            // 执行标签查询
            List<Template> templates = templateMapper.selectByTag(tag);
            
            return Result.success("查询成功", templates);
        } catch (Exception e) {
            log.error("根据标签查询模板失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 读取文件内容
     * @param file 文件
     * @return 文件内容
     * @throws IOException IO异常
     */
    private String readFile(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
} 