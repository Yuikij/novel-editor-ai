package com.soukon.novelEditorAi.model.chapter;

import lombok.Data;
import reactor.core.publisher.Flux;

import java.util.concurrent.CountDownLatch;

@Data
public class PlanContext {
    private PlanState planState;
    private String planId;
    private Flux<String> planStream;
    private CountDownLatch completionLatch;
    private String message;
    //进度
    private Integer progress;

    public PlanContext(String planId) {
        this.planId = planId;
    }

    /**
     * 通知服务器前端已完成流的消费
     */
    public void notifyConsumptionCompleted() {
        if (this.completionLatch != null) {
            this.completionLatch.countDown();
        }
    }

}
