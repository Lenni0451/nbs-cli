package net.lenni0451.nbscli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import net.lenni0451.nbscli.model.ConverterFormats;

import java.io.File;

public class CLI {

    public static class Main {
        @Parameter(names = {"-h", "--help"},
                description = "Show this help message",
                help = true)
        public boolean help;
    }

    @Parameters(commandNames = "info",
            commandDescription = "Get information about a given NBS file")
    public static class Info {
        @Parameter(names = {"-i", "--input"},
                description = "The NBS file to get information about",
                required = true,
                placeholder = "<file.nbs>")
        public File file;

        @Parameter(names = {"-f", "--full"},
                description = "Show full information about the NBS file")
        public boolean full = false;
    }

    @Parameters(commandNames = "convert",
            commandDescription = "Convert a given song file to a different format")
    public static class Convert {
        @Parameter(names = {"-i", "--input"},
                description = "The song file to convert. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.mid>")
        public File inputFile;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                placeholder = "<format>")
        public String inputFormat;

        @Parameter(names = {"-o", "--output"},
                description = "The output file to write the converted song to. The format is determined by the file extension, unless the --format option is specified. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs>")
        public File outputFile;

        @Parameter(names = {"-f", "-of", "--format", "--output-format"},
                description = "The format to convert the song to. Required if the file extension can't be used to determine the format, or when writing to standard output. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                placeholder = "<format>")
        public String format;

        @Parameter(names = {"-w", "--overwrite"},
                description = "Overwrite the output file if it already exists")
        public boolean overwrite = false;
    }

}
