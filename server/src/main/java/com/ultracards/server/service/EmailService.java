package com.ultracards.server.service;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.auth.VerificationCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
public class EmailService {
    @Value("${app.code.validity-minutes:10}")
    private int CODE_VALIDITY_MINUTES;
    @Value("${spring.mail.username}")
    String fromEmail;
    @Value("${app.mail.from.name}")
    String fromName;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;


    public void sendVerificationEmail(UserEntity user, VerificationCode code) throws MessagingException, UnsupportedEncodingException {
        var context = new Context();
        context.setVariable("code", code.getCode());
        context.setVariable("verificationCodeValidityMinutes", CODE_VALIDITY_MINUTES);

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
