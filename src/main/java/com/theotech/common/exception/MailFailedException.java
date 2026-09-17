package com.theotech.common.exception;

import org.springframework.http.HttpStatus;

/**
 * An e-mail could not be sent. {@code MAIL_NOT_CONFIGURED} when the SMTP account is missing (fix it in
 * Settings), {@code MAIL_FAILED} when the mail server refused the message (wrong password, blocked port…).
 */
public class MailFailedException extends AppException {

    public MailFailedException(String code, String message) {
        super(HttpStatus.BAD_GATEWAY, code, message);
    }
}
