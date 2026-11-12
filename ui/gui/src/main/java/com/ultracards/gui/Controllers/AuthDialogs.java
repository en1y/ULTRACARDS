package com.ultracards.gui.Controllers;

import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import javax.security.auth.login.AccountLockedException;

final class AuthDialogs {
    private AuthDialogs() {
    }

    static boolean notSignedIn(GatewayAppClient gateway) {
        if (gateway.tokenHolder().getToken() != null) {
            return false;
        }

        var emailDialog = new TextInputDialog();
        emailDialog.setTitle("Sign in or create account");
        emailDialog.setHeaderText("A verification code will be sent to your email");
        emailDialog.setContentText("Email:");
        var email = emailDialog.showAndWait().map(String::trim).filter(value -> !value.isBlank());
        if (email.isEmpty()) {
            return true;
        }

        try {
            gateway.authentication().sendVerificationEmail(email.get(), gateway.tokenHolder());
        } catch (RuntimeException | AccountLockedException exception) {
            showError("Could not send code", exception.getMessage());
            System.out.println(exception.getMessage());
            return true;
        }

        var codeDialog = new TextInputDialog();
        codeDialog.setTitle("Verify your email");
        codeDialog.setHeaderText("Enter the six-digit code sent to " + email.get());
        codeDialog.setContentText("Verification code:");
        var code = codeDialog.showAndWait().map(String::trim).filter(value -> value.matches("\\d{6}"));
        if (code.isEmpty()) {
            return true;
        }

        try {
            gateway.authentication().verifyCode(new VerificationCodeDTO(code.get(), email.get()), gateway.tokenHolder());
            var usernameDTO = gateway.profile().getUsername();
            var username = usernameDTO == null ? null : usernameDTO.getUsername();
            if (username == null || username.isBlank()) {
                var usernameDialog = new TextInputDialog();
                usernameDialog.setTitle("Choose a username");
                usernameDialog.setHeaderText("Set the name other players will see");
                usernameDialog.setContentText("Username:");
                var chosenName = usernameDialog.showAndWait().map(String::trim).filter(value -> value.length() >= 3);
                if (chosenName.isEmpty()) {
                    return true;
                }
                gateway.profile().updateUsername(chosenName.get());
            }
            return gateway.tokenHolder().getToken() == null;
        } catch (RuntimeException exception) {
            showError("Sign-in failed", exception.getMessage());
            return true;
        }
    }

    private static void showError(String title, String message) {
        var alert = new Alert(Alert.AlertType.ERROR,
                message == null || message.isBlank() ? title : message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
