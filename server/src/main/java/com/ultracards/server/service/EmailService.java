package com.ultracards.server.service;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.VerificationCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromEmail;
    private final String fromName;

    public EmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${spring.mail.username}") String fromEmail,
            @Value("${app.mail.from.name}") String fromName) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    public void sendVerificationEmail(UserEntity user, VerificationCode code) throws MessagingException, UnsupportedEncodingException {
        var context = new Context();
        context.setVariable("code", code.getCode());

        var username = user.getUsername();
        if (username.isEmpty())
            context.setVariable("username", "new player");
        else
            context.setVariable("username", username);

        var htmlContent = templateEngine.process("verification-mail-template", context);

        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");

        helper.setFrom(new InternetAddress(fromEmail, fromName));

        helper.setTo(user.getEmail());
        helper.setSubject("ULTRACARDS Verification Code");
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
}
