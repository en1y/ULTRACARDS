package com.ultracards.server.bootstrap;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import java.util.ArrayList;

public final class BootstrapAdminCommand {
    private BootstrapAdminCommand() {}

    public static int run(String[] args) {
        String email = null;
        String username = null;
        var force = false;
        var springArgs = new ArrayList<String>();
        for (var i = 0; i < args.length; i++) {
            var arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                usage();
                return 0;
            }
            if ("--force".equals(arg)) { force = true; continue; }
            if (arg.startsWith("--email=")) { email = arg.substring("--email=".length()); continue; }
            if (arg.startsWith("--username=")) { username = arg.substring("--username=".length()); continue; }
            if ("--email".equals(arg) && i + 1 < args.length) { email = args[++i]; continue; }
            if ("--username".equals(arg) && i + 1 < args.length) { username = args[++i]; continue; }
            if (arg.startsWith("--spring.") || arg.startsWith("--app.database.")) { springArgs.add(arg); continue; }
            System.err.println("Unknown bootstrap option: " + arg);
            usage();
            return 2;
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            System.err.println("A valid --email is required");
            usage();
            return 2;
        }

        var application = new SpringApplication(BootstrapAdminConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        try (var context = application.run(springArgs.toArray(String[]::new))) {
            var result = context.getBean(BootstrapAdminExecutor.class)
                    .execute(email.trim().toLowerCase(), username, force);
            var stream = result.exitCode() == 0 ? System.out : System.err;
            stream.println(result.message());
            return result.exitCode();
        } catch (Exception ex) {
            System.err.println("Failed to bootstrap administrator: " + rootMessage(ex));
            return 7;
        }
    }

    private static String rootMessage(Throwable error) {
        var current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void usage() {
        System.out.println("Usage: java -jar server.jar bootstrap-admin --email <address> [--username <name>] [--force] [--spring.profiles.active=<profile>]");
    }
}
