package com.soukon.novelEditorAi.model.chapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class PlanRes {
    @JsonPropertyDescription("写作目标")
    String goal;
    @JsonPropertyDescription("完成计划之后的情节完成百分比")
    Integer completePercent;
    @JsonPropertyDescription("写作计划")
    List<PlanDetailRes> planList;


}
