package com.enterprise.idp.service;

import com.enterprise.idp.domain.UserAccount;
import com.enterprise.idp.dto.AuthDtos;
import com.enterprise.idp.exception.DuplicateResourceException;
import com.enterprise.idp.repository.UserAccountRepository;
import com.enterprise.idp.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and login backed by JWT access tokens. */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        if (repository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (repository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }
        UserAccount account = new UserAccount();
        account.setUsername(request.username());
        account.setEmail(request.email());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole("ROLE_USER");
        UserAccount saved = repository.save(account);
        return issueToken(saved);
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        UserAccount account = repository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!account.isEnabled()
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return issueToken(account);
    }

    private AuthDtos.TokenResponse issueToken(UserAccount account) {
        String token = tokenProvider.generateToken(account.getUsername(), account.getRole());
        return new AuthDtos.TokenResponse(
                token,
                "Bearer",
                tokenProvider.getExpiresInSeconds(),
                account.getUsername(),
                account.getRole());
    }
}
