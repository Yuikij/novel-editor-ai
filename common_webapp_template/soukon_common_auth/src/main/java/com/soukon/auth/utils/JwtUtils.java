package com.soukon.auth.utils;

import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

import static com.soukon.auth.constant.AuthConstants.*;

@Slf4j
public class JwtUtils {

    public static String generateToken(JSONObject obj) {
        JWTCreator.Builder builder = JWT.create();
        obj.forEach((k, v) -> {
            builder.withClaim(k, v.toString());
        });

        // Optionally, store session data in Redis
//        redisTemplate.opsForValue().set("SESSION:" + username, token, EXPIRATION_TIME, TimeUnit.MILLISECONDS);
        return builder
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(Algorithm.HMAC512(SECRET_KEY.getBytes()));
    }

    public static JSONObject validateToken(String token) {
        try {
            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC512(SECRET_KEY.getBytes()))
                    .build()
                    .verify(token.replace("Bearer ", ""));

            Map<String, Claim> claims = decodedJWT.getClaims();
            JSONObject res = new JSONObject();
            claims.forEach((k, v) -> {
                res.put(k, v.asString());
            });
            return res;
        } catch (Exception e) {
            log.error("解析JWT失败");
            return null;
        }
    }

    public static void invalidateToken(String username) {
//        redisTemplate.delete("SESSION:" + username);
    }

    public static void refreshToken(String username) {
//        redisTemplate.delete("SESSION:" + username);
    }
}
