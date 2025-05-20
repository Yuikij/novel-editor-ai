package com.soukon.novelEditorAi.model.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 条目请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemRequest {
    
    /**
     * 条目ID
     */
    private Long id;
    
    /**
     * 条目名称
     */
    private String name;
    
    /**
     * 条目标签
     */
    private String tags;
    
    /**
     * 条目描述
     */
    private String description;
    
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