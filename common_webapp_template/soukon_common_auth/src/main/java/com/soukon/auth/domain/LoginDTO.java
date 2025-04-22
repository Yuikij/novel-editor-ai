package com.soukon.auth.domain;

import lombok.Data;

@Data
public class LoginDTO {
    private long userid;
    private String username;
    private String password;
    private String uid;
}
