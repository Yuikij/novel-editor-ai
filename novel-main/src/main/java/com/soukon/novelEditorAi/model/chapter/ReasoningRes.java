package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class ReasoningRes {
    @JsonPropertyDescription("章节写作内容")
    String writingText;
    @JsonPropertyDescription("规划后的情节列表")
    List<PlotRes> plotList;
}
