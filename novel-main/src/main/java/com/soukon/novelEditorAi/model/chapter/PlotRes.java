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
    @JsonPropertyDescription("当前情节的完成百分比")
    private Integer completionPercentage;
    //    目标字数
    @JsonPropertyDescription("目标字数")
    private Integer wordCountGoal;

    @Override
    public String toString() {
        return "情节：{" +
               "\n\t标题: '" + title + '\'' +
               ",\n\t描述: '" + description + '\'' +
               ",\n\t顺序: " + sortOrder +
               ",\n\t完成状态: '" + status + '\'' +
               ",\n\t完成百分比: " + completionPercentage + "%" +
               ",\n\t目标字数: " + wordCountGoal +
               "\n}";
    }
}
