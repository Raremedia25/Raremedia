package com.theotech.common.mail;

/** Sends one HTML e-mail. The SMTP implementation reads its account from the settings table. */
public interface EmailSender {

    /**
     * @param to one address, or several separated by commas
     * @throws com.theotech.common.exception.MailFailedException when SMTP is not configured or refuses the message
     */
    void send(String to, String subject, String html);
}
