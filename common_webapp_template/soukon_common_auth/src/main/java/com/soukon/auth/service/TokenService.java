package com.soukon.auth.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.soukon.auth.domain.LoginDTO;
import com.soukon.auth.domain.LoginVO;
import com.soukon.auth.domain.UserBO;
import com.soukon.auth.utils.HttpUtils;
import com.soukon.auth.utils.JwtUtils;
import com.soukon.redis.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.soukon.auth.constant.AuthConstants.*;


@Component
public class TokenService {
    @Autowired
    private RedisService redisService;

    public LoginVO createToken(UserBO userBO) {
        redisService.setCacheObject(LOGIN_TOKEN_KEY + userBO.getUid(), userBO, EXPIRATION_MINUTES, TimeUnit.MINUTES);
        LoginVO loginVO = new LoginVO();
        String token = JwtUtils.generateToken(JSON.parseObject(JSONObject.toJSONString(userBO), JSONObject.class));
        loginVO.setToken(token);
        loginVO.setUsername(userBO.getUsername());
        loginVO.setExpiresIn(EXPIRATION_MINUTES);
        return loginVO;
    }

    public UserBO getUser(String token) {
        JSONObject jsonObject = JwtUtils.validateToken(token);
        if (jsonObject == null) {
            return null;
        }
        return redisService.getCacheObject(LOGIN_TOKEN_KEY + jsonObject.getString("uid"));
    }


    public UserBO getUser() {
        String token = HttpUtils.getToken();
        return getUser(token);
    }

}
