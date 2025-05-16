package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class PlanDetailRes {
    @JsonPropertyDescription("写作目标字数")
    Integer goalWordCount;
    @JsonPropertyDescription("计划具体内容")
    String planContent;
}
