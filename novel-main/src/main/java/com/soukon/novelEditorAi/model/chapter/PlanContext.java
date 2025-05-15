package com.soukon.novelEditorAi.model.chapter;

import lombok.Data;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class PlanContext {
    private PlanState planState;
    private String planId;
    private Flux<String> planStream;
    private CountDownLatch completionLatch;
    public PlanContext(String planId) {
        this.planId = planId;
    }
    


}
