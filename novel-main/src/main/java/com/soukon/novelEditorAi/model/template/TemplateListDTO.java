package com.soukon.novelEditorAi.model.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板列表DTO - 不包含content字段，用于列表查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateListDTO {
    
    /**
     * 模板ID
     */
    private Long id;
    
    /**
     * 模板名称
     */
    private String name;
    
    /**
     * 模板标签
     */
    private String tags;
} 