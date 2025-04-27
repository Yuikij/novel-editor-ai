package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.mapper.PlotMapper;
import com.soukon.novelEditorAi.service.PlotService;
import org.springframework.stereotype.Service;

@Service
public class PlotServiceImpl extends ServiceImpl<PlotMapper, Plot> implements PlotService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
} 