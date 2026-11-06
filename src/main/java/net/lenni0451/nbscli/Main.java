package net.lenni0451.nbscli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.commands.ConvertCommand;
import net.lenni0451.nbscli.commands.InfoCommand;

public class Main {

    public static void main(final String[] args) {
        CLI.Main main = new CLI.Main();
        CLI.Info info = new CLI.Info();
        CLI.Convert convert = new CLI.Convert();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(main)
                .addCommand(info)
                .addCommand(convert)
                .build();
        try {
            jCommander.parse(args);
            if (main.help || jCommander.getParsedCommand() == null) {
                jCommander.usage();
                return;
            }
            switch (jCommander.getParsedCommand()) {
                case "info" -> new InfoCommand(info).execute();
                case "convert" -> new ConvertCommand(convert).execute();
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
