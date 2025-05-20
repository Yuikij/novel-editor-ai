package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 条目Mapper接口
 */
@Mapper
public interface ItemMapper extends BaseMapper<Item> {
    
    /**
     * 根据标签查询条目列表
     * @param tag 标签
     * @return 条目列表
     */
    @Select("SELECT * FROM items WHERE tags LIKE CONCAT('%', #{tag}, '%')")
    List<Item> selectByTag(@Param("tag") String tag);
} 