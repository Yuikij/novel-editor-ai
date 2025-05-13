package com.soukon.novelEditorAi.model.chapter;

import lombok.Data;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;

@Data
public class PlanContext {
    private PlanState planState;
    private String planId;
    private Flux<String> planStream;

    public PlanContext(String planId) {
        this.planId = planId;
    }

}
