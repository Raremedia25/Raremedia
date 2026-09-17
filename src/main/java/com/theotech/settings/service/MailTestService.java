package com.theotech.settings.service;

import com.theotech.common.exception.ValidationException;
import com.theotech.common.mail.EmailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** "Send test e-mail" on the Settings page. */
@Service
public class MailTestService {

    private final SettingsService settings;
    private final EmailSender email;

    public MailTestService(SettingsService settings, EmailSender email) {
        this.settings = settings;
        this.email = email;
    }

    /** @return the address the test went to */
    @PreAuthorize("hasRole('ADMIN')")
    public String sendTest() {
        String to = settings.reportEmail();
        if (to.isEmpty()) {
            throw new ValidationException("REPORT_EMAIL_MISSING", "Enter the report e-mail address first");
        }
        String company = settings.companyName();
        email.send(to, company + " - test e-mail",
                "<p>This is a test message from <b>" + escape(company) + "</b>.</p>" +
                "<p>E-mail is set up correctly: sales reports will arrive at this address.</p>");
        return to;
    }

    static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
