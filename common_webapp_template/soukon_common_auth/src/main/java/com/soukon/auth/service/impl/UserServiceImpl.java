package com.soukon.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.auth.domain.UserDO;
import com.soukon.auth.domain.UserDTO;
import com.soukon.auth.mapper.UserMapper;
import com.soukon.auth.service.UserService;
import com.soukon.core.http.ApiResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {
    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LambdaQueryWrapper<UserDO> wrapper = Wrappers.lambdaQuery(UserDO.class);
        wrapper.eq(UserDO::getUsername, username);
        List<UserDO> list = list(wrapper);
        if (list.isEmpty()) {

        }
        return list.get(0);
    }

    @Override
    public ApiResponse<Object> register(UserDTO user) {
        String newPassword = passwordEncoder.encode(user.getPassword());
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(user, userDO);
        userDO.setPassword(newPassword);
        save(userDO);
        return ApiResponse.success();
    }
}
