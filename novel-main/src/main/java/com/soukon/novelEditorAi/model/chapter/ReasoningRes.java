package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class ReasoningRes {
    @JsonPropertyDescription("结构化的章节写作计划")
    String writingPlan;
    @JsonPropertyDescription("写作完成之后的情节情况")
    List<PlotRes> plotList;
}
