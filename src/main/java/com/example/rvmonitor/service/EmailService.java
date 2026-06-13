package com.example.rvmonitor.service;

import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends "new RV available" alerts. Mirrors banff-monitor's EmailService: if SMTP
 * isn't configured (no username), it logs the alert instead of failing so the
 * monitor still works for local/dev runs.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${notification.email.to}")
    private String to;

    @Value("${notification.email.from}")
    private String from;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendNewListings(Watch watch, List<RvListing> listings) {
        String subject = String.format("🚐 %d new RV match%s — %s",
                listings.size(), listings.size() == 1 ? "" : "es", watch.getName());
        String body = buildBody(watch, listings);

        if (mailUsername == null || mailUsername.isBlank()) {
            logger.info("[email disabled — no SMTP username] would send:\nSubject: {}\n{}", subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom(from);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            logger.info("Sent alert to {} — {} listing(s) for '{}'", to, listings.size(), watch.getName());
        } catch (Exception e) {
            logger.error("Failed to send alert email: {}", e.getMessage());
        }
    }

    private String buildBody(Watch watch, List<RvListing> listings) {
        StringBuilder sb = new StringBuilder();
        sb.append("New RV match(es) for your watch:\n");
        sb.append("  ").append(watch.getName()).append('\n');
        sb.append("  ").append(watch.getStartDate()).append(" → ").append(watch.getEndDate());
        sb.append("  near ").append(watch.getAddress()).append("\n\n");
        for (RvListing rv : listings) {
            sb.append("• ").append(rv.summary()).append('\n');
            if (rv.getUrl() != null) {
                sb.append("    ").append(rv.getUrl()).append('\n');
            }
        }
        sb.append("\n— rv-monitor");
        return sb.toString();
    }
}
