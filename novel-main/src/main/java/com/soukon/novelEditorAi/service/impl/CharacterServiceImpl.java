package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.mapper.CharacterMapper;
import com.soukon.novelEditorAi.service.CharacterService;
import org.springframework.stereotype.Service;

@Service
public class CharacterServiceImpl extends ServiceImpl<CharacterMapper, Character> implements CharacterService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
} 