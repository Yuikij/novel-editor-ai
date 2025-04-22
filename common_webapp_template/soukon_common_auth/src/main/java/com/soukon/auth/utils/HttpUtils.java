package com.soukon.auth.utils;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.auth.constant.AuthConstants;
import com.soukon.core.http.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.PrintWriter;

@Slf4j
public class HttpUtils {

    public static  <T> void parseFromResponse(HttpServletResponse response, ApiResponse<T> apiResponse) {
        try {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(apiResponse.getCode());
            PrintWriter out = response.getWriter();
            out.write(new ObjectMapper().writeValueAsString(apiResponse));
            out.flush();
            out.close();
        } catch (Exception e) {
            log.error("Response转换失败", e);
        }
    }

    public static ServletRequestAttributes getRequestAttributes()
    {
        try
        {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            return (ServletRequestAttributes) attributes;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 获取request
     */
    public static HttpServletRequest getRequest()
    {
        try
        {
            return getRequestAttributes().getRequest();
        }
        catch (Exception e)
        {
            return null;
        }
    }


    /**
     * 根据request获取请求token
     */
    public static String getToken() {
        String token = getRequest().getHeader(AuthConstants.AUTHORIZATION);
        // String token = request.getParameter("token");
        if (StringUtils.isNotEmpty(token) && token.startsWith(AuthConstants.TOKEN_PREFIX)) {
            token = token.replace(AuthConstants.TOKEN_PREFIX, "");
        }
        return token;
    }

}
