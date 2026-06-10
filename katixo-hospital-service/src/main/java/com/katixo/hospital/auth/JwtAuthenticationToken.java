package com.katixo.hospital.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public class JwtAuthenticationToken implements Authentication {

    private final JwtClaims claims;
    private final List<SimpleGrantedAuthority> authorities;
    private boolean authenticated = true;

    public JwtAuthenticationToken(JwtClaims claims) {
        this.claims = claims;
        this.authorities = claims.getRoles() == null ? List.of()
                : claims.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return claims;
    }

    @Override
    public Object getPrincipal() {
        return claims.getUsername();
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return claims.getUsername();
    }

    public JwtClaims getClaims() {
        return claims;
    }
}
