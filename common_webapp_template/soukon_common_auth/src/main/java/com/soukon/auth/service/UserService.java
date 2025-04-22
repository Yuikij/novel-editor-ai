package com.soukon.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.auth.domain.UserDO;
import com.soukon.auth.domain.UserDTO;
import com.soukon.core.http.ApiResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends IService<UserDO>, UserDetailsService {
    ApiResponse<Object> register(UserDTO user);
}
