package com.soukon.novelEditorAi.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.annotation.SelectField;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询工具类
 * 配合 @SelectField 注解使用，动态控制查询字段
 */
@Slf4j
public class QueryUtils {
    
    // 缓存已解析的字段信息，避免重复反射
    private static final ConcurrentHashMap<Class<?>, List<String>> FIELD_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 根据VO类的@SelectField注解配置，动态设置查询字段
     * 
     * @param wrapper 查询包装器
     * @param entityClass 实体类
     * @param voClass VO类（包含@SelectField注解）
     * @param <T> 实体类型
     */
    public static <T> void fillSelect(LambdaQueryWrapper<T> wrapper, Class<T> entityClass, Class<?> voClass) {
        List<String> selectFields = getSelectFields(voClass);
        if (!selectFields.isEmpty()) {
            wrapper.select(entityClass, i -> selectFields.contains(i.getProperty()));
            log.debug("动态查询字段设置完成，实体类: {}, VO类: {}, 查询字段: {}", 
                    entityClass.getSimpleName(), voClass.getSimpleName(), selectFields);
        }
    }
    
    /**
     * 获取需要查询的字段列表
     * 
     * 逻辑：
     * 1. 如果有@SelectField注解且enable=true的字段，则只查询这些字段
     * 2. 如果没有enable=true的@SelectField注解，则查询所有字段，但排除enable=false的字段
     * 
     * @param clazz VO类
     * @return 需要查询的字段列表
     */
    public static List<String> getSelectFields(Class<?> clazz) {
        // 先从缓存中获取
        return FIELD_CACHE.computeIfAbsent(clazz, QueryUtils::parseSelectFields);
    }
    
    /**
     * 解析类的查询字段
     */
    private static List<String> parseSelectFields(Class<?> clazz) {
        List<String> enabledFields = new ArrayList<>();  // 明确启用的字段
        List<String> allValidFields = new ArrayList<>(); // 所有有效字段（排除disabled的）
        boolean hasEnabledAnnotation = false;
        
        // 获取所有字段，包括父类字段
        List<Field> allFields = getAllFields(clazz);
        
        for (Field field : allFields) {
            String fieldName = field.getName();
            
            if (field.isAnnotationPresent(SelectField.class)) {
                SelectField annotation = field.getAnnotation(SelectField.class);
                String columnName = annotation.column().isEmpty() ? fieldName : annotation.column();
                
                if (annotation.enable()) {
                    enabledFields.add(columnName);
                    allValidFields.add(columnName);
                    hasEnabledAnnotation = true;
                }
                // enable=false的字段不加入allValidFields
            } else {
                // 没有注解的字段默认包含
                allValidFields.add(fieldName);
            }
        }
        
        List<String> result = hasEnabledAnnotation ? enabledFields : allValidFields;
        log.debug("解析查询字段完成，类: {}, 字段: {}", clazz.getSimpleName(), result);
        return result;
    }
    
    /**
     * 获取类的所有字段，包括父类字段
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> allFields = new ArrayList<>();
        Class<?> currentClass = clazz;
        
        while (currentClass != null && currentClass != Object.class) {
            allFields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        
        return allFields;
    }
    
    /**
     * 清除字段缓存（用于测试或动态更新场景）
     */
    public static void clearCache() {
        FIELD_CACHE.clear();
        log.debug("查询字段缓存已清除");
    }
    
    /**
     * 清除指定类的字段缓存
     */
    public static void clearCache(Class<?> clazz) {
        FIELD_CACHE.remove(clazz);
        log.debug("已清除类 {} 的查询字段缓存", clazz.getSimpleName());
    }
} 