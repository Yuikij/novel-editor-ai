package com.soukon.novelEditorAi.model.naming;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 名称建议实体
 * 包含一个名称及其解释
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NameSuggestion {
    private String name;
    private String explanation;
} 