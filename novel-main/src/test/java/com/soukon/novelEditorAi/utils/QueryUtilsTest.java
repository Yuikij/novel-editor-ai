package com.soukon.novelEditorAi.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.model.template.TemplateBasicVO;
import com.soukon.novelEditorAi.model.template.TemplateChatContextVO;
import com.soukon.novelEditorAi.model.template.TemplateExistenceVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * QueryUtils测试类
 * 展示注解方案的使用方法
 */
@SpringBootTest
public class QueryUtilsTest {
    
    @Test
    public void testTemplateBasicVO() {
        // 测试模板基本信息查询（包含向量化状态）
        List<String> fields = QueryUtils.getSelectFields(TemplateBasicVO.class);
        System.out.println("TemplateBasicVO查询字段: " + fields);
        
        // 预期结果：[id, name, tags, vector_status, vector_progress, vector_start_time, vector_end_time, vector_error_message]
    }
    
    @Test
    public void testTemplateChatContextVO() {
        // 测试对话上下文查询（只需要基本信息）
        List<String> fields = QueryUtils.getSelectFields(TemplateChatContextVO.class);
        System.out.println("TemplateChatContextVO查询字段: " + fields);
        
        // 预期结果：[id, name, tags, vector_status]
    }
    
    @Test
    public void testTemplateExistenceVO() {
        // 测试存在性检查（只需要ID）
        List<String> fields = QueryUtils.getSelectFields(TemplateExistenceVO.class);
        System.out.println("TemplateExistenceVO查询字段: " + fields);
        
        // 预期结果：[id]
    }
    
    @Test
    public void testDifferentScenarios() {
        System.out.println("=== 不同场景的查询字段对比 ===");
        
        // 场景1：向量化进度查询
        LambdaQueryWrapper<Template> progressWrapper = new LambdaQueryWrapper<>();
        QueryUtils.fillSelect(progressWrapper, Template.class, TemplateBasicVO.class);
        progressWrapper.eq(Template::getId, 1L);
        System.out.println("向量化进度查询：使用TemplateBasicVO，包含所有向量化相关字段");
        
        // 场景2：对话上下文查询
        LambdaQueryWrapper<Template> chatWrapper = new LambdaQueryWrapper<>();
        QueryUtils.fillSelect(chatWrapper, Template.class, TemplateChatContextVO.class);
        chatWrapper.eq(Template::getId, 1L);
        System.out.println("对话上下文查询：使用TemplateChatContextVO，只包含对话需要的字段");
        
        // 场景3：存在性检查
        LambdaQueryWrapper<Template> existenceWrapper = new LambdaQueryWrapper<>();
        QueryUtils.fillSelect(existenceWrapper, Template.class, TemplateExistenceVO.class);
        existenceWrapper.eq(Template::getId, 1L);
        System.out.println("存在性检查：使用TemplateExistenceVO，只查询ID字段");
    }
    
    /**
     * 性能对比示例
     */
    @Test
    public void performanceComparisonExample() {
        System.out.println("=== 性能对比示例 ===");
        
        // 传统方式：查询所有字段（包括大的content字段）
        LambdaQueryWrapper<Template> fullWrapper = new LambdaQueryWrapper<>();
        fullWrapper.eq(Template::getId, 1L);
        System.out.println("传统方式：查询所有字段，包括content");
        
        // 优化方式1：向量化进度查询
        LambdaQueryWrapper<Template> progressWrapper = new LambdaQueryWrapper<>();
        QueryUtils.fillSelect(progressWrapper, Template.class, TemplateBasicVO.class);
        progressWrapper.eq(Template::getId, 1L);
        System.out.println("优化方式1：向量化进度查询，排除content");
        
        // 优化方式2：对话上下文查询
        LambdaQueryWrapper<Template> chatWrapper = new LambdaQueryWrapper<>();
        QueryUtils.fillSelect(chatWrapper, Template.class, TemplateChatContextVO.class);
        chatWrapper.eq(Template::getId, 1L);
        System.out.println("优化方式2：对话上下文查询，只查询必要字段");
        
        // 优化方式3：存在性检查
        LambdaQueryWrapper<Template> existenceWrapper = new LambdaQueryWrapper<>();
        QueryUtils.fillSelect(existenceWrapper, Template.class, TemplateExistenceVO.class);
        existenceWrapper.eq(Template::getId, 1L);
        System.out.println("优化方式3：存在性检查，只查询ID字段");
        
        System.out.println("性能提升：优化方式3 > 优化方式2 > 优化方式1 > 传统方式");
    }
} 