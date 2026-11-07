package net.lenni0451.nbscli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.commands.ConvertCommand;
import net.lenni0451.nbscli.commands.EditCommand;
import net.lenni0451.nbscli.commands.InfoCommand;
import net.lenni0451.nbscli.commands.RenderCommand;
import net.lenni0451.nbscli.commands.play.PlayCommand;
import net.lenni0451.nbscli.utils.CommandListUsageFormatter;
import net.lenni0451.nbscli.utils.CommandUsageFormatter;

public class Main {

    public static void main(final String[] args) {
        CLI.Main main = new CLI.Main();
        CLI.Info info = new CLI.Info();
        CLI.Convert convert = new CLI.Convert();
        CLI.Edit edit = new CLI.Edit();
        CLI.Render render = new CLI.Render();
        CLI.Play play = new CLI.Play();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(main)
                .addCommand(info)
                .addCommand(convert)
                .addCommand(edit)
                .addCommand(render)
                .addCommand(play)
                .programName("nbs-cli")
                .build();
        jCommander.setUsageFormatter(new CommandListUsageFormatter(jCommander));
        jCommander.getCommands().values().forEach(command -> command.setUsageFormatter(new CommandUsageFormatter(command, jCommander.getProgramName())));
        try {
            jCommander.parse(args);
            if (main.version) {
                System.out.println("NBS CLI version ${version} (${commit_hash})");
                return;
            }
            if (main.help || jCommander.getParsedCommand() == null) {
                jCommander.usage();
                return;
            }
            JCommander command = jCommander.findCommandByAlias(jCommander.getParsedCommand());
            if (((CLI.Command) command.getObjects().get(0)).help) {
                command.usage();
                return;
            }
            switch (jCommander.getParsedCommand()) {
                case "info" -> new InfoCommand(info).execute();
                case "convert" -> new ConvertCommand(convert).execute();
                case "edit" -> new EditCommand(edit).execute();
                case "render" -> new RenderCommand(render).execute();
                case "play" -> new PlayCommand(play).execute();
                default -> jCommander.usage();
            }
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            Throwable cause = e;
            while ((cause = cause.getCause()) != null) {
                System.err.println("Caused by: " + cause.getMessage());
            }
            System.exit(1);
        } catch (Throwable t) {
            System.err.println("An unexpected error occurred: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(2);
        }
    }

}
