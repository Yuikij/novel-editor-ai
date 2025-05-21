package com.soukon.novelEditorAi.model.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模板请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRequest {
    
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
    
    /**
     * 模板内容
     */
    private String content;
    
    /**
     * 批量操作的ID列表
     */
    private List<Long> ids;
    
    /**
     * 页码
     */
    private Integer page;
    
    /**
     * 每页大小
     */
    private Integer size;
} 