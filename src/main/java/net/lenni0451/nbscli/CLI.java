package net.lenni0451.nbscli;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.IParameterSplitter;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.model.InstrumentNames;
import net.raphimc.noteblocklib.util.MinecraftOctaveCorrection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    @Parameters(commandNames = "edit",
            commandDescription = "Edit a song file. Edits are applied in the order: resampling, instruments, custom instruments, notes, metadata. The song keeps its format, use the convert command to change it")
    public static class Edit {
        // ---------- Input/Output ----------
        @Parameter(names = {"-i", "--input"},
                description = "The song file to edit. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs>",
                order = 0)
        public File inputFile;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 1)
        public String inputFormat;

        @Parameter(names = {"-o", "--output"},
                description = "The file to write the edited song to. The song is written in the same format as the input. May be the same file as the input when used with --overwrite",
                required = true,
                placeholder = "<file.nbs>",
                order = 2)
        public File outputFile;

        @Parameter(names = {"-w", "--overwrite"},
                description = "Overwrite the output file if it already exists",
                order = 3)
        public boolean overwrite = false;

        // ---------- Resampling ----------
        @Parameter(names = "--tempo",
                description = "[Resampling] Change the tick speed of the song without changing its musical speed. Lowering the tempo reduces timing precision",
                placeholder = "<tps>",
                order = 10)
        public Float tempo;

        @Parameter(names = "--precompute-tempo",
                description = "[Resampling] Precompute the NBS tempo changer: converts a song with dynamic tempo changes into one with a static tempo, for players that don't support tempo changes",
                order = 11)
        public boolean precomputeTempo = false;

        // ---------- Instruments ----------
        @Parameter(names = "--instrument",
                description = "[Instruments] Replace or remove a vanilla instrument. Can be specified multiple times. <to> is an instrument name or 'remove'. Instruments: " + InstrumentNames.LIST,
                placeholder = "<from>=<to|remove>",
                splitter = NoSplitter.class,
                order = 20)
        public List<String> instruments = new ArrayList<>();

        @Parameter(names = "--custom-instrument",
                description = "[Instruments] Replace a custom instrument with a vanilla one or remove it (NBS only). Can be specified multiple times. The custom instrument is selected by its index (as shown by 'info --full') or its name",
                placeholder = "<index|name>=<to|remove>",
                splitter = NoSplitter.class,
                order = 21)
        public List<String> customInstruments = new ArrayList<>();

        // ---------- Notes ----------
        @Parameter(names = "--octave-correction",
                description = "[Notes] How to handle notes outside the Minecraft octave range. none: keep the key. instrument-shift: switch to a higher/lower sounding instrument, keeps the musical key (usually sounds best). transpose: move the key by octaves into the range. clamp: set keys below/above the range to the lowest/highest key.",
                placeholder = "<none|instrument-shift|transpose|clamp>",
                converter = OctaveCorrectionConverter.class,
                order = 30)
        public MinecraftOctaveCorrection octaveCorrection;

        @Parameter(names = "--min-volume",
                description = "[Notes] Remove all notes with a volume at or below the given percentage (0-100). 0 removes only silent notes",
                placeholder = "<percent>",
                order = 31)
        public Integer minVolume;

        @Parameter(names = "--remove-duplicates",
                description = "[Notes] Remove notes which would be played multiple times in the same tick. Useful for large MIDI conversions or after lowering the tempo",
                order = 32)
        public boolean removeDuplicates = false;

        // ---------- Metadata ----------
        @Parameter(names = "--title", description = "[Metadata] Set the title (NBS, MCSP2). Use an empty string to clear it", placeholder = "<text>", order = 40)
        public String title;

        @Parameter(names = "--author", description = "[Metadata] Set the author (NBS, MCSP2). Use an empty string to clear it", placeholder = "<text>", order = 41)
        public String author;

        @Parameter(names = "--original-author", description = "[Metadata] Set the original author (NBS, MCSP2). Use an empty string to clear it", placeholder = "<text>", order = 42)
        public String originalAuthor;

        @Parameter(names = "--description", description = "[Metadata] Set the description (NBS). Use an empty string to clear it", placeholder = "<text>", order = 43)
        public String description;

        @Parameter(names = "--source-file", description = "[Metadata] Set the source file name (NBS). Use an empty string to clear it", placeholder = "<text>", order = 44)
        public String sourceFileName;

        @Parameter(names = "--auto-save", description = "[Metadata] Enable or disable auto save (NBS)", placeholder = "<true|false>", arity = 1, order = 45)
        public Boolean autoSave;

        @Parameter(names = "--auto-save-interval", description = "[Metadata] Set the auto save interval in minutes, 0-255 (NBS, MCSP2)", placeholder = "<minutes>", order = 46)
        public Integer autoSaveInterval;

        @Parameter(names = "--time-signature", description = "[Metadata] Set the time signature, 0-255 (NBS)", placeholder = "<n>", order = 47)
        public Integer timeSignature;

        @Parameter(names = "--minutes-spent", description = "[Metadata] Set the minutes spent (NBS, MCSP2)", placeholder = "<n>", order = 48)
        public Integer minutesSpent;

        @Parameter(names = "--left-clicks", description = "[Metadata] Set the left click count (NBS, MCSP2)", placeholder = "<n>", order = 49)
        public Integer leftClicks;

        @Parameter(names = "--right-clicks", description = "[Metadata] Set the right click count (NBS, MCSP2)", placeholder = "<n>", order = 50)
        public Integer rightClicks;

        @Parameter(names = "--note-blocks-added", description = "[Metadata] Set the added note blocks count (NBS, MCSP2)", placeholder = "<n>", order = 51)
        public Integer noteBlocksAdded;

        @Parameter(names = "--note-blocks-removed", description = "[Metadata] Set the removed note blocks count (NBS, MCSP2)", placeholder = "<n>", order = 52)
        public Integer noteBlocksRemoved;

        @Parameter(names = "--loop", description = "[Metadata] Enable or disable looping (NBS)", placeholder = "<true|false>", arity = 1, order = 53)
        public Boolean loop;

        @Parameter(names = "--max-loop-count", description = "[Metadata] Set the maximum loop count, 0-255, 0 = infinite (NBS)", placeholder = "<n>", order = 54)
        public Integer maxLoopCount;

        @Parameter(names = "--loop-start-tick", description = "[Metadata] Set the tick the loop starts at, 0-32767 (NBS)", placeholder = "<tick>", order = 55)
        public Integer loopStartTick;
    }

    public static class NoSplitter implements IParameterSplitter {
        @Override
        public List<String> split(final String value) {
            return List.of(value);
        }
    }

    public static class OctaveCorrectionConverter implements IStringConverter<MinecraftOctaveCorrection> {
        @Override
        public MinecraftOctaveCorrection convert(final String value) {
            try {
                return MinecraftOctaveCorrection.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new ParameterException("Invalid octave correction: " + value + ". Supported values are: none, instrument-shift, transpose, clamp");
            }
        }
    }

}
