package net.lenni0451.nbscli.utils;

import com.beust.jcommander.DefaultUsageFormatter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;

public class CommandUsageFormatter extends DefaultUsageFormatter {

    private final JCommander command;
    private final String programName;

    public CommandUsageFormatter(final JCommander command, final String programName) {
        super(command);
        this.command = command;
        this.programName = programName;
    }

    @Override
    public void appendMainLine(final StringBuilder out, final boolean hasOptions, final boolean hasCommands, final int indentCount, final String indent) {
        StringBuilder mainLine = new StringBuilder();
        mainLine.append(indent).append("Usage: ").append(this.programName).append(' ').append(this.command.getProgramName());
        if (hasOptions) mainLine.append(" [options]");
        this.wrapDescription(out, indentCount, mainLine);
        out.append('\n');

        Parameters parameters = this.command.getObjects().get(0).getClass().getAnnotation(Parameters.class);
        if (parameters != null && !parameters.commandDescription().isEmpty()) {
            out.append('\n');
            this.wrapDescription(out, indent.length() + 2, indent + "  " + parameters.commandDescription());
            out.append('\n');
        }
    }

}
