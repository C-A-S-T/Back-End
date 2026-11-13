package com.example.cast.auth.security;

import com.example.cast.user.entity.AccountStatus;
import com.example.cast.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 서명/클레임 검증 이후 매 요청마다 사용자 존재와 현재 계정 상태를 확인한다. */
@Component
@RequiredArgsConstructor
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    public static final String ACCOUNT_DISABLED = "account_disabled";
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
            if (userId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
        var user = users.findById(userId).orElseThrow(() ->
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ACCOUNT_DISABLED));
        }
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(userId), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
