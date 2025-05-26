package com.soukon.novelEditorAi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 查询字段控制注解
 * 用于标记实体类中需要查询的字段，避免查询不必要的大字段
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SelectField {
    
    /**
     * 数据库字段名，如果为空则使用字段名
     */
    String column() default "";
    
    /**
     * 是否启用该字段查询
     */
    boolean enable() default true;
    
    /**
     * 字段描述，用于文档说明
     */
    String description() default "";
} 