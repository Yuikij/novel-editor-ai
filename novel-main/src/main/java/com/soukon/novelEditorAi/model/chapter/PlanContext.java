package com.soukon.novelEditorAi.model.chapter;

import lombok.Data;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;

@Data
public class PlanContext {
    private PlanState planState;
    private String planId;
    private HashMap<Integer,Flux<String>> planStreams;

    public PlanContext(String planId) {
        this.planId = planId;
    }

}
