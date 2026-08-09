package com.smartadmin.security;

import java.util.Collection;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** 登录主体：携带稳定 userId，避免仅依赖用户名（改名后 JWT 仍可用）。 */
@Getter
public class LoginUser extends User {

    private final Long id;

    public LoginUser(
            Long id,
            String username,
            String password,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.id = id;
    }
}
