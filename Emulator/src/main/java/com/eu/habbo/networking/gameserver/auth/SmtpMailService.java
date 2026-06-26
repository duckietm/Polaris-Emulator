package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public final class SmtpMailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpMailService.class);

    private SmtpMailService() {}

    public static boolean send(String toAddress, String subject, String body) {
        try {
            String provider = Emulator.getConfig().getValue("smtp.provider", "own").toLowerCase();
            String username = Emulator.getConfig().getValue("smtp.username", "");
            String password = Emulator.getConfig().getValue("smtp.password", "");
            String fromAddr = Emulator.getConfig().getValue("smtp.from_address", username);
            String fromName = Emulator.getConfig().getValue("smtp.from_name", "Habbo Hotel");

            if (toAddress == null || toAddress.isEmpty() || fromAddr == null || fromAddr.isEmpty()) {
                LOGGER.warn("SMTP send aborted — missing to/from address (to={}, from={})", toAddress, fromAddr);
                return false;
            }

            String host;
            int port;
            boolean useSsl;
            boolean useTls;

            switch (provider) {
                case "gmail" -> {
                    host = "smtp.gmail.com";
                    port = 465;
                    useSsl = true;
                    useTls = false;
                }
                case "sendgrid" -> {
                    host = "smtp.sendgrid.net";
                    port = 587;
                    useSsl = false;
                    useTls = true;
                }
                case "mailgun" -> {
                    host = "smtp.mailgun.org";
                    port = 587;
                    useSsl = false;
                    useTls = true;
                }
                default -> {
                    host = Emulator.getConfig().getValue("smtp.host", "localhost");
                    port = Emulator.getConfig().getInt("smtp.port", 587);
                    useSsl = Emulator.getConfig().getBoolean("smtp.use_ssl", false);
                    useTls = Emulator.getConfig().getBoolean("smtp.use_tls", true);
                }
            }

            var props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.auth", String.valueOf(!username.isEmpty()));
            if (useTls) props.put("mail.smtp.starttls.enable", "true");
            if (useSsl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            }
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");

            Session session = username.isEmpty()
                    ? Session.getInstance(props)
                    : Session.getInstance(props, new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

            var message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddr, fromName, "UTF-8"));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");

            Transport.send(message);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to send SMTP mail to " + toAddress, e);
            return false;
        }
    }
}
