package com.ultracards.gui.Controllers;

import com.ultracards.gateway.app.GatewayAppClient;
import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Controls the in-window, multi-step email authentication flow. */
public class AuthController {
    @FXML private StackPane overlay;
    @FXML private VBox emailStep;
    @FXML private VBox codeStep;
    @FXML private VBox usernameStep;
    @FXML private TextField emailInput;
    @FXML private TextField usernameInput;
    @FXML private TextField code0;
    @FXML private TextField code1;
    @FXML private TextField code2;
    @FXML private TextField code3;
    @FXML private TextField code4;
    @FXML private TextField code5;
    @FXML private Label emailError;
    @FXML private Label codeError;
    @FXML private Label usernameError;
    @FXML private Label subtitle;

    private GatewayAppClient gateway;
    private Consumer<String> onSignedIn = ignored -> { };
    private String pendingEmail;

    public void configure(GatewayAppClient gateway, Consumer<String> onSignedIn) {
        this.gateway = gateway;
        this.onSignedIn = onSignedIn;
    }

    public void open() {
        if (gateway.tokenHolder().getToken() != null) {
            onSignedIn.accept("Already signed in");
            return;
        }
        pendingEmail = null;
        clearErrors();
        emailInput.clear();
        setStep(emailStep);
        subtitle.setText("Use your email to sign in or finish setting up your account.");
        overlay.setManaged(true);
        overlay.setVisible(true);
        emailInput.requestFocus();
    }

    @FXML
    private void close() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        pendingEmail = null;
    }

    @FXML
    private void closeOnBackdrop(javafx.scene.input.MouseEvent event) {
        if (event.getTarget() == overlay) {
            close();
        }
    }

    @FXML
    private void consumeClick(javafx.scene.input.MouseEvent event) {
        event.consume();
    }

    @FXML
    private void sendCode() {
        clearErrors();
        var email = emailInput.getText() == null ? "" : emailInput.getText().trim();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            emailError.setText("Enter a valid email address.");
            return;
        }
        try {
            gateway.authentication().sendVerificationEmail(email, gateway.tokenHolder());
            pendingEmail = email;
            setStep(codeStep);
            subtitle.setText("Enter the 6-digit code sent to " + email + ".");
            codeFields()[0].requestFocus();
        } catch (RuntimeException | javax.security.auth.login.AccountLockedException exception) {
            emailError.setText(messageFor(exception, "Could not send the code. Try again."));
        }
    }

    @FXML
    private void verifyCode() {
        clearErrors();
        var code = new StringBuilder();
        for (var field : codeFields()) {
            code.append(field.getText() == null ? "" : field.getText());
        }
        if (pendingEmail == null || !code.toString().matches("\\d{6}")) {
            codeError.setText("Enter the 6-digit code.");
            return;
        }
        try {
            gateway.authentication().verifyCode(
                    new VerificationCodeDTO(code.toString(), pendingEmail), gateway.tokenHolder());
            var usernameDTO = gateway.profile().getUsername();
            var username = usernameDTO == null ? null : usernameDTO.getUsername();
            if (username == null || username.isBlank()) {
                setStep(usernameStep);
                subtitle.setText("Set the name other players will see.");
                usernameInput.requestFocus();
                return;
            }
            finish(username);
        } catch (RuntimeException exception) {
            codeError.setText(messageFor(exception, "Could not verify the code. Try again."));
        }
    }

    @FXML
    private void saveUsername() {
        usernameError.setText("");
        var username = usernameInput.getText() == null ? "" : usernameInput.getText().trim();
        if (username.length() < 3) {
            usernameError.setText("Username must be at least 3 characters.");
            return;
        }
        try {
            gateway.profile().updateUsername(username);
            finish(username);
        } catch (RuntimeException exception) {
            usernameError.setText(messageFor(exception, "Could not save username. Try again."));
        }
    }

    @FXML
    private void onCodeTyped(KeyEvent event) {
        var field = (TextField) event.getSource();
        var value = field.getText() == null ? "" : field.getText().replaceAll("\\D", "");
        if (value.length() > 1) {
            value = value.substring(value.length() - 1);
        }
        field.setText(value);
        if (!value.isEmpty()) {
            var fields = codeFields();
            for (var index = 0; index < fields.length - 1; index++) {
                if (fields[index] == field) {
                    fields[index + 1].requestFocus();
                    break;
                }
            }
        }
    }

    @FXML
    private void onCodeKeyPressed(KeyEvent event) {
        var fields = codeFields();
        var field = (TextField) event.getSource();
        if (event.getCode() == KeyCode.BACK_SPACE || event.getCode() == KeyCode.LEFT) {
            for (var index = 1; index < fields.length; index++) {
                if (fields[index] == field && (event.getCode() == KeyCode.LEFT || field.getText().isEmpty())) {
                    fields[index - 1].requestFocus();
                    break;
                }
            }
        } else if (event.getCode() == KeyCode.RIGHT) {
            for (var index = 0; index < fields.length - 1; index++) {
                if (fields[index] == field) {
                    fields[index + 1].requestFocus();
                    break;
                }
            }
        }
    }

    private void finish(String username) {
        close();
        onSignedIn.accept("Signed in as " + username);
    }

    private TextField[] codeFields() {
        return new TextField[]{code0, code1, code2, code3, code4, code5};
    }

    private void setStep(Node step) {
        for (var candidate : new VBox[]{emailStep, codeStep, usernameStep}) {
            var active = candidate == step;
            candidate.setVisible(active);
            candidate.setManaged(active);
        }
    }

    private void clearErrors() {
        emailError.setText("");
        codeError.setText("");
        usernameError.setText("");
    }

    private String messageFor(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback : exception.getMessage();
    }
}
