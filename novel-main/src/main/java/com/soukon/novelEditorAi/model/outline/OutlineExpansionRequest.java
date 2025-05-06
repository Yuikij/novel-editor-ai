package com.soukon.novelEditorAi.model.outline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 大纲扩展请求类
 * 包含已有情节点ID列表和目标数量
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutlineExpansionRequest {
    
    /**
     * 已有的情节点ID列表
     */
    private List<Long> existingPlotPointIds = new ArrayList<>();
    
    /**
     * 目标情节点总数
     */
    private Integer targetCount = 12;
} 