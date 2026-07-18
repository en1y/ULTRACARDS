package com.ultracards.cli;

import com.ultracards.gateway.dto.auth.VerificationCodeDTO;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "login", description = "Log in with an emailed verification code.")
class Login extends CliCommand {
    @Option(names = "--email", required = true, paramLabel = "ADDRESS", description = "Administrator email address.")
    String email;

    public Integer call() {
        return root().withClient(client -> {
            client.authentication().sendVerificationEmail(email);
            if (!root().quiet) System.err.println("Verification code sent to " + email + ".");
            var code = root().promptSecret("Verification code: ");
            if (code == null || !code.matches("\\d{6}"))
                throw new IllegalArgumentException("Verification code must be six digits");
            if (!client.authentication().verifyCode(new VerificationCodeDTO(code, email), client.tokenHolder()))
                throw new IllegalArgumentException("Verification code was rejected");
            root().emit(client.authentication().getProfile());
            return 0;
        });
    }
}

@Command(name = "logout", description = "Revoke the current session and forget its local token.")
class Logout extends CliCommand {
    public Integer call() {
        root().withClient(client -> {
            client.authentication().logout(client.tokenHolder());
            return null;
        });
        root().store.tokenFor(root().selectedProfile(), null);
        return ok("Logged out");
    }
}

@Command(name = "whoami", description = "Show the account authenticated on the active server.")
class WhoAmI extends CliCommand {
    public Integer call() {
        return root().withClient(client -> ok(client.authentication().getProfile()));
    }
}
