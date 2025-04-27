package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.World;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorldMapper extends BaseMapper<World> {
    // MyBatis-Plus provides basic CRUD operations
    // You can add custom methods here if needed
} 