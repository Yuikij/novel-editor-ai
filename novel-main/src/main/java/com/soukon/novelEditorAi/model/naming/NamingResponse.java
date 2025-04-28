package com.soukon.novelEditorAi.model.naming;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 命名响应实体
 * 包含一组名称建议
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NamingResponse {
    private List<NameSuggestion> names;
} 