package com.soukon.auth.handler;

import com.soukon.auth.domain.LoginDTO;
import com.soukon.auth.domain.LoginVO;
import com.soukon.auth.domain.UserBO;
import com.soukon.auth.domain.UserDO;
import com.soukon.auth.service.TokenService;
import com.soukon.auth.utils.HttpUtils;
import com.soukon.core.http.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private TokenService tokenService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest httpServletRequest, HttpServletResponse response, Authentication auth) throws IOException {
        String uid = UUID.randomUUID().toString();
        UserDO user = (UserDO) auth.getPrincipal();
        UserBO userBO = new UserBO();
        userBO.setUsername(user.getUsername());
        userBO.setUid(uid);
        userBO.setUserid(user.getId());
        LoginVO loginVO = tokenService.createToken(userBO);
        log.info("登录成功 {}", user.getUsername());
        HttpUtils.parseFromResponse(response, ApiResponse.success(loginVO));
    }

}
