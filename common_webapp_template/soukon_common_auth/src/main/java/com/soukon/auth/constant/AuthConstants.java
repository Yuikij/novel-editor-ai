package com.soukon.auth.constant;

public class AuthConstants {
    public static final String SECRET_KEY = "SecretKeyToGenJWTs";
    public static final long EXPIRATION_TIME = 864_000_000; // 10 days
    /**
     * 权限缓存前缀
     */
    public final static String LOGIN_TOKEN_KEY = "login_tokens:";
    public final static String AUTHORIZATION = "Authorization";
    public final static long EXPIRATION_MINUTES = 720;
    /**
     * 令牌前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

}
