package com.theotech.settings.dto;

/** The SMTP account as stored in settings (internal; the password never leaves the server). */
public record MailSettings(String host, int port, String username, String password, String from) {

    public boolean configured() {
        return notBlank(host) && notBlank(username) && notBlank(password);
    }

    public String fromOrUsername() {
        return notBlank(from) ? from : username;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
