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


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("写作目标字数: ").append(goalWordCount == null ? "未知" : goalWordCount + "字").append("\n");
        sb.append("计划内容: ").append(planContent == null ? "无" : planContent).append("\n");
        return sb.toString();
    }


}
