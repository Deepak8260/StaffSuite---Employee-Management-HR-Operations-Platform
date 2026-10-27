package com.staffsuite.service;

/** Delivers a password-reset token to a user. Swap in an email implementation later. */
public interface PasswordResetNotifier {

    void send(String email, String token);
}
