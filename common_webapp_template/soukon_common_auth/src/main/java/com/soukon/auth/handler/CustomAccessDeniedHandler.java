package com.soukon.auth.handler;

import com.soukon.auth.utils.HttpUtils;
import com.soukon.core.http.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        HttpUtils.parseFromResponse(response,
                ApiResponse.error(HttpServletResponse.SC_FORBIDDEN, "Access denied: " + accessDeniedException.getMessage()));
    }
}
