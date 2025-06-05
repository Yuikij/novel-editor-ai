package com.soukon.novelEditorAi.model.chapter;

import lombok.Data;

@Data
public class PlanInfo {
    private String planId;
    private Long chapterId;


    @Override
    public String toString() {
        return "计划id为：'" + planId + '\'' +
               ", 章节id为：" + chapterId;
    }
}
