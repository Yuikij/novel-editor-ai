package com.soukon.novelEditorAi.model.template;

import com.soukon.novelEditorAi.annotation.SelectField;
import lombok.Data;

/**
 * 模板存在性检查VO
 * 用于只需要检查模板是否存在的场景
 */
@Data
public class TemplateExistenceVO {
    
    @SelectField(description = "模板ID")
    private Long id;
    
    // 只查询ID字段，用于检查模板是否存在
} 