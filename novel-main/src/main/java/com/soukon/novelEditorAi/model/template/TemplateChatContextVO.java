package com.soukon.novelEditorAi.model.template;

import com.soukon.novelEditorAi.annotation.SelectField;
import lombok.Data;

/**
 * 模板对话上下文VO
 * 用于对话功能中获取模板基本信息
 */
@Data
public class TemplateChatContextVO {
    
    @SelectField(description = "模板ID")
    private Long id;
    
    @SelectField(description = "模板名称")
    private String name;
    
    @SelectField(description = "模板标签")
    private String tags;
    
    @SelectField(description = "向量化状态")
    private String vectorStatus;
    
    // 排除content字段和其他向量化进度字段，只保留对话需要的基本信息
} 