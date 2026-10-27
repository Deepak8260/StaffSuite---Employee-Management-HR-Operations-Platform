package com.staffsuite.service;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Development stand-in: no email is sent, the token is written to the server log. */
@Slf4j
@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    @Override
    public void send(String email, String token) {
        log.warn("Password reset requested for {} (email delivery not implemented). Reset token: {}", email, token);
    }
}
