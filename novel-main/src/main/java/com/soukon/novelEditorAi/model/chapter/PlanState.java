package com.soukon.novelEditorAi.model.chapter;

public enum PlanState {
    /**
     * 计划中
     */
    PLANNING(0, "计划中"),
    /**
     * 进行中
     */
    IN_PROGRESS(1, "执行中"),
    /**
     * 生成中
     */
    GENERATING(2, "生成中"),

    /**
     * 已完成
     */
    COMPLETED(3, "已完成");

    private final int code;
    private final String message;

    PlanState(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
