package com.ultracards.cli;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.TerminalBuilder;
import picocli.AutoComplete;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Parameters;
import picocli.shell.jline3.PicocliJLineCompleter;

@Command(name = "shell", description = "Open an interactive shell with history and completion.")
class Shell extends CliCommand {
    public Integer call() throws Exception {
        try (var terminal = TerminalBuilder.builder().system(true).build()) {
            var commandLine = root().commandLine();
            var completer = new PicocliJLineCompleter(commandLine.getCommandSpec());
            var reader = LineReaderBuilder.builder().terminal(terminal).completer(completer)
                    .variable(org.jline.reader.LineReader.HISTORY_FILE, root().store.historyFile()).build();
            terminal.writer().println((root().colorsEnabled() ? Ansi.ON : Ansi.OFF).string(
                    "@|bold,cyan ULTRACARDS ADMIN SHELL|@  @|faint Type help, exit, or press Tab to explore.|@"));
            terminal.writer().flush();
            while (true) {
                try {
                    var profile = root().store.activeProfile() == null ? "no-server" : root().store.activeProfile();
                    var prompt = (root().colorsEnabled() ? Ansi.ON : Ansi.OFF)
                            .string("@|bold,cyan ultracards|@@|faint [" + profile + "]|@> ");
                    var line = reader.readLine(prompt).trim();
                    if (line.equals("exit") || line.equals("quit")) return 0;
                    if (line.isBlank()) continue;
                    var args = new org.jline.reader.impl.DefaultParser().parse(line, 0).words().toArray(String[]::new);
                    root().commandLine().execute(args);
                } catch (UserInterruptException ignored) {
                } catch (EndOfFileException ex) {
                    return 0;
                }
            }
        }
    }
}

@Command(name = "completion", description = "Generate Bash or Zsh completion setup.")
class Completion extends CliCommand {
    @Parameters(index = "0", defaultValue = "bash") String shell;

    public Integer call() {
        var script = AutoComplete.bash("ultracards-admin", root().commandLine());
        if (shell.equalsIgnoreCase("zsh")) System.out.println("autoload -U +X bashcompinit && bashcompinit");
        else if (!shell.equalsIgnoreCase("bash")) throw new IllegalArgumentException("Completion supports bash or zsh");
        System.out.print(script);
        return 0;
    }
}
