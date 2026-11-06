package net.lenni0451.nbscli.utils;

import com.beust.jcommander.DefaultUsageFormatter;
import com.beust.jcommander.JCommander;

public class CommandListUsageFormatter extends DefaultUsageFormatter {

    private final JCommander commander;

    public CommandListUsageFormatter(final JCommander commander) {
        super(commander);
        this.commander = commander;
    }

    @Override
    public void appendCommands(final StringBuilder out, final int indentCount, final int descriptionIndent, final String indent) {
        int nameWidth = this.commander.getCommands().keySet().stream().mapToInt(String::length).max().orElse(0);
        out.append('\n').append(indent).append("  Commands:\n");
        for (String name : this.commander.getCommands().keySet()) {
            String line = indent + s(4) + name + s(nameWidth - name.length() + 3) + this.getCommandDescription(name);
            this.wrapDescription(out, indent.length() + 4 + nameWidth + 3, line);
            out.append('\n');
        }
        out.append('\n').append(indent).append("  Run '").append(this.commander.getProgramName()).append(" <command> --help' to show the options of a command\n");
    }

}
