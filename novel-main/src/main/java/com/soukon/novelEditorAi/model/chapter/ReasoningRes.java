package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class ReasoningRes {
//    @JsonPropertyDescription("章节写作内容")
//    String writingText;
    @JsonPropertyDescription("规划后的情节列表")
    List<PlotRes> plotList;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("当前情节列表：");
        if (plotList != null && !plotList.isEmpty()) {
            for (PlotRes plot : plotList) {
                sb.append("\n").append(plot.toString());
            }
        } else {
            sb.append("无情节");
        }
        return sb.toString();
    }
}
