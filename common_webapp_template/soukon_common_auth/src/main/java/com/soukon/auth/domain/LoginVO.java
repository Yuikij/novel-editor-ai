package com.soukon.auth.domain;

import lombok.Data;

@Data
public class LoginVO {

    private String username;
    private String token;
    private long expiresIn;
}
