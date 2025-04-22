package com.soukon.auth.handler;

import com.soukon.auth.utils.HttpUtils;
import com.soukon.core.http.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CutomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        HttpUtils.parseFromResponse(response,
                ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: " + authException.getMessage()));
    }
}
