package com.soukon.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.auth.utils.HttpUtils;
import com.soukon.core.http.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 认证失败处理 - 前后端分离情况下返回json数据格式
 */
@Slf4j
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest httpServletRequest, HttpServletResponse response, AuthenticationException e) {
        HttpUtils.parseFromResponse(response, ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage()));
    }
}
