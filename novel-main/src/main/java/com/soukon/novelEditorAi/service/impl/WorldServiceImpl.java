package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.mapper.WorldMapper;
import com.soukon.novelEditorAi.service.WorldService;
import org.springframework.stereotype.Service;

@Service
public class WorldServiceImpl extends ServiceImpl<WorldMapper, World> implements WorldService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
} 