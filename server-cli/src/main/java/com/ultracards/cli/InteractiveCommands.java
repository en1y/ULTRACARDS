package com.ultracards.cli;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;
import picocli.AutoComplete;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.util.ArrayList;

@Command(name = "shell", description = "Open an interactive shell with history and completion.")
class Shell extends CliCommand {
    private static boolean active;
    private static Terminal activeTerminal;

    public Integer call() throws Exception {
        if (active) return ok("Already in the interactive shell");
        return open(root());
    }

    static int open(UltracardsAdminCli root) throws Exception {
        try (var terminal = TerminalBuilder.builder().system(true).build()) {
            return run(root, terminal);
        }
    }

    static int run(UltracardsAdminCli root, Terminal terminal) {
        var reader = reader(root, terminal);
        var ansi = root.colorsEnabled() ? Ansi.ON : Ansi.OFF;
        active = true;
        activeTerminal = terminal;
        try {
            WelcomeScreen.print(root, terminal, ansi);
            while (true) {
                try {
                    var profile = root.store.activeProfile() == null ? "no-server" : root.store.activeProfile();
                    var prompt = ansi.string("@|bold,red ultracards|@@|faint [" + profile + "]|@ @|red ❯|@ ");
                    var line = reader.readLine(prompt).trim();
                    if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) return 0;
                    if (line.isBlank()) continue;
                    var args = new org.jline.reader.impl.DefaultParser().parse(line, 0).words().toArray(String[]::new);
                    root.commandLine().execute(args);
                } catch (UserInterruptException ignored) {
                } catch (EndOfFileException ex) {
                    return 0;
                }
            }
        } finally {
            activeTerminal = null;
            active = false;
        }
    }

    static void redraw(UltracardsAdminCli root) {
        if (activeTerminal == null)
            throw new UltracardsAdminCli.CliStateException("The clear command is available inside the interactive shell");
        activeTerminal.puts(Capability.clear_screen);
        WelcomeScreen.print(root, activeTerminal, root.colorsEnabled() ? Ansi.ON : Ansi.OFF);
    }

    static LineReader reader(UltracardsAdminCli root, Terminal terminal) {
        var commandLine = root.commandLine();
        var completer = completer(commandLine.getCommandSpec());
        var reader = LineReaderBuilder.builder().terminal(terminal).completer(completer)
                .variable(LineReader.HISTORY_FILE, root.store.historyFile())
                .option(LineReader.Option.AUTO_MENU, true)
                .option(LineReader.Option.AUTO_LIST, true)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
        configureKeys(reader, terminal);
        return reader;
    }

    static Completer completer(CommandSpec spec) {
        var delegate = new PicocliJLineCompleter(spec);
        return (reader, line, candidates) -> {
            var generated = new ArrayList<Candidate>();
            delegate.complete(reader, line, generated);
            var mixed = generated.stream().anyMatch(candidate -> candidate.value().startsWith("-"))
                    && generated.stream().anyMatch(candidate -> !candidate.value().startsWith("-"));
            for (var candidate : generated) {
                var option = candidate.value().startsWith("-");
                var group = mixed ? (option ? "Options" : "Commands") : candidate.group();
                candidates.add(new Candidate(candidate.value(), candidate.displ(), group, candidate.descr(),
                        candidate.suffix(), candidate.key(), candidate.complete(), option ? 1 : 0));
            }
        };
    }

    private static void configureKeys(LineReader reader, Terminal terminal) {
        var keys = reader.getKeyMaps().get(LineReader.MAIN);
        bind(keys, LineReader.UP_LINE_OR_HISTORY, KeyMap.key(terminal, Capability.key_up), "\033[A", "\033OA");
        bind(keys, LineReader.DOWN_LINE_OR_HISTORY, KeyMap.key(terminal, Capability.key_down), "\033[B", "\033OB");
        bind(keys, LineReader.FORWARD_CHAR, KeyMap.key(terminal, Capability.key_right), "\033[C", "\033OC");
        bind(keys, LineReader.BACKWARD_CHAR, KeyMap.key(terminal, Capability.key_left), "\033[D", "\033OD");
        bind(keys, LineReader.BEGINNING_OF_LINE, KeyMap.key(terminal, Capability.key_home),
                "\033[H", "\033OH", "\033[1~", "\033[7~");
        bind(keys, LineReader.END_OF_LINE, KeyMap.key(terminal, Capability.key_end),
                "\033[F", "\033OF", "\033[4~", "\033[8~");
        bind(keys, LineReader.DELETE_CHAR, KeyMap.key(terminal, Capability.key_dc), "\033[3~");
        bind(keys, LineReader.BACKWARD_DELETE_CHAR, KeyMap.key(terminal, Capability.key_backspace), "\177");
        bind(keys, LineReader.EXPAND_OR_COMPLETE, "\t");
        bind(keys, LineReader.REVERSE_MENU_COMPLETE, "\033[Z");
        bind(keys, LineReader.BEGINNING_OF_HISTORY, "\033[5~");
        bind(keys, LineReader.END_OF_HISTORY, "\033[6~");

        bind(keys, LineReader.BACKWARD_WORD, "\033b", "\033[1;5D", "\033[5D");
        bind(keys, LineReader.FORWARD_WORD, "\033f", "\033[1;5C", "\033[5C");
        bind(keys, LineReader.BACKWARD_KILL_WORD, "\033\177", KeyMap.ctrl('W'));
        bind(keys, LineReader.KILL_WORD, "\033d", "\033[3;5~");
        bind(keys, LineReader.BEGINNING_OF_LINE, KeyMap.ctrl('A'));
        bind(keys, LineReader.END_OF_LINE, KeyMap.ctrl('E'));
        bind(keys, LineReader.BACKWARD_CHAR, KeyMap.ctrl('B'));
        bind(keys, LineReader.FORWARD_CHAR, KeyMap.ctrl('F'));
        bind(keys, LineReader.UP_LINE_OR_HISTORY, KeyMap.ctrl('P'));
        bind(keys, LineReader.DOWN_LINE_OR_HISTORY, KeyMap.ctrl('N'));
        bind(keys, LineReader.BACKWARD_KILL_LINE, KeyMap.ctrl('U'));
        bind(keys, LineReader.KILL_LINE, KeyMap.ctrl('K'));
        bind(keys, LineReader.CLEAR_SCREEN, KeyMap.ctrl('L'));
        bind(keys, LineReader.HISTORY_INCREMENTAL_SEARCH_BACKWARD, KeyMap.ctrl('R'));
    }

    private static void bind(KeyMap<Binding> keys, String widget, String... sequences) {
        var binding = new Reference(widget);
        for (var sequence : sequences) {
            if (sequence != null && !sequence.isEmpty()) keys.bind(binding, sequence);
        }
    }
}

@Command(name = "clear", description = "Clear the shell and redraw the startup screen.")
class Clear extends CliCommand {
    public Integer call() {
        Shell.redraw(root());
        return 0;
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
