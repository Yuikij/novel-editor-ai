package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class PlanRes {
    @JsonPropertyDescription("写作目标")
    String goal;
//    @JsonPropertyDescription("完成计划之后的情节完成百分比")
//    Integer completePercent;
    @JsonPropertyDescription("写作计划")
    List<PlanDetailRes> planList;


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("写作目标: ").append(goal == null ? "无" : goal).append("\n");
//        sb.append("完成计划之后的情节完成百分比: ").append(completePercent == null ? "未知" : completePercent + "%").append("\n");
        sb.append("写作计划:\n");
        if (planList == null || planList.isEmpty()) {
            sb.append("无写作计划");
        } else {
            for (int i = 0; i < planList.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(planList.get(i).toString()).append("\n");
            }
        }
        return sb.toString();
    }

}
