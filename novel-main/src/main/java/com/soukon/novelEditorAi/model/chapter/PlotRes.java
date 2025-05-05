package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class PlotRes {
    private String title;
    private String description;
    private Integer sortOrder;
    @JsonPropertyDescription("当前情节的完成情况")
    private String status;
    @JsonPropertyDescription("当前情节的完成百分比，而不是本章节的完成百分比")

    private Integer completionPercentage;
}
