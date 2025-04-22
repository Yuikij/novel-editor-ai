package com.soukon.auth.controller;

import com.soukon.auth.domain.UserDTO;
import com.soukon.auth.service.UserService;
import com.soukon.core.http.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ApiResponse<Object> register(@RequestBody UserDTO user){
        return userService.register(user);
    }

    @PostMapping("/test1")
    public ApiResponse<Object> test1(@RequestBody UserDTO user){
        return ApiResponse.success();
    }
}
