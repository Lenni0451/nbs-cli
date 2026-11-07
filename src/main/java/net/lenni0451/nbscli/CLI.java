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
                help = true,
                order = 0)
        public boolean help;

        @Parameter(names = {"-v", "--version"},
                description = "Show the version of the application",
                help = true,
                order = 1)
        public boolean version;
    }

    public abstract static class Command {
        @Parameter(names = {"-h", "--help"},
                description = "Show the help message of this command",
                help = true,
                order = 1000)
        public boolean help;
    }

    @Parameters(commandNames = "info",
            commandDescription = "Get information about a given NBS file")
    public static class Info extends Command {
        @Parameter(names = {"-i", "--input"},
                description = "The NBS file to get information about",
                required = true,
                placeholder = "<file.nbs>",
                order = 0)
        public File file;

        @Parameter(names = {"-f", "--full"},
                description = "Show full information about the NBS file",
                order = 1)
        public boolean full = false;
    }

    @Parameters(commandNames = "convert",
            commandDescription = "Convert a given song file to a different format")
    public static class Convert extends Command {
        @Parameter(names = {"-i", "--input"},
                description = "The song file to convert. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.mid>",
                order = 0)
        public File inputFile;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 1)
        public String inputFormat;

        @Parameter(names = {"-o", "--output"},
                description = "The output file to write the converted song to. The format is determined by the file extension, unless the --format option is specified. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs>",
                order = 2)
        public File outputFile;

        @Parameter(names = {"-f", "-of", "--format", "--output-format"},
                description = "The format to convert the song to. Required if the file extension can't be used to determine the format, or when writing to standard output. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 3)
        public String format;

        @Parameter(names = {"-w", "--overwrite"},
                description = "Overwrite the output file if it already exists",
                order = 4)
        public boolean overwrite = false;
    }

    @Parameters(commandNames = "edit",
            commandDescription = "Edit a song file. Edits are applied in the order: resampling, instruments, custom instruments, notes, metadata. The song keeps its format, use the convert command to change it")
    public static class Edit extends Command {
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

    @Parameters(commandNames = "render",
            commandDescription = "Render a song to an audio file. Use the convert command to export to other song formats")
    public static class Render extends Command {
        // ---------- Input/Output ----------
        @Parameter(names = {"-i", "--input"},
                description = "The song file to render. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs>",
                order = 0)
        public File inputFile;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 1)
        public String inputFormat;

        @Parameter(names = {"-o", "--output"},
                description = "The audio file to write. The format is determined by the file extension, unless the --format option is specified. Standard output is only supported for WAV and Opus",
                required = true,
                placeholder = "<file.wav>",
                order = 2)
        public File outputFile;

        @Parameter(names = {"-f", "-of", "--format", "--output-format"},
                description = "The audio format to render to. Required if the file extension can't be used to determine the format, or when writing to standard output. Supported formats are: WAV, FLAC, MP3, Opus",
                placeholder = "<format>",
                order = 3)
        public String format;

        @Parameter(names = {"-w", "--overwrite"},
                description = "Overwrite the output file if it already exists",
                order = 4)
        public boolean overwrite = false;

        @Parameter(names = {"-q", "--quiet"},
                description = "Don't print the render progress",
                order = 5)
        public boolean quiet = false;

        // ---------- Audio ----------
        @Parameter(names = "--sample-rate",
                description = "[Audio] The sample rate in Hz, 8000-192000. Default: 48000",
                placeholder = "<hz>",
                order = 10)
        public Integer sampleRate;

        @Parameter(names = "--channels",
                description = "[Audio] The channel layout. Default: stereo",
                placeholder = "<mono|stereo>",
                order = 11)
        public String channels;

        @Parameter(names = "--wav-encoding",
                description = "[Audio] The PCM sample encoding (WAV). u8: unsigned 8-bit, s16/s24/s32: signed 16/24/32-bit, f32: 32-bit float. Default: s16",
                placeholder = "<u8|s16|s24|s32|f32>",
                order = 12)
        public String wavEncoding;

        @Parameter(names = "--flac-bits",
                description = "[Audio] The bits per sample, 4-32 (FLAC). Default: 16",
                placeholder = "<bits>",
                order = 13)
        public Integer flacBitsPerSample;

        @Parameter(names = "--flac-compression",
                description = "[Audio] The compression level, 0 (fastest) - 8 (smallest) (FLAC). Default: 6",
                placeholder = "<level>",
                order = 14)
        public Integer flacCompressionLevel;

        @Parameter(names = "--bitrate-mode",
                description = "[Audio] The bitrate mode (MP3, Opus). cbr: constant, abr: average, vbr: variable. Default: vbr",
                placeholder = "<cbr|abr|vbr>",
                order = 15)
        public String bitrateMode;

        @Parameter(names = "--bitrate",
                description = "[Audio] The (target) bitrate in kbps. MP3: 8-320, only used with cbr/abr, default 224. Opus: 8-384, default 128",
                placeholder = "<kbps>",
                order = 16)
        public Integer bitrate;

        @Parameter(names = "--quality",
                description = "[Audio] The quality in percent, 0-100 (MP3 with vbr). Default: 80",
                placeholder = "<percent>",
                order = 17)
        public Integer quality;

        // ---------- Playback ----------
        @Parameter(names = "--volume",
                description = "[Playback] The master volume in percent, 0-100. Default: 90",
                placeholder = "<percent>",
                order = 20)
        public Integer volume;

        @Parameter(names = "--timing-jitter",
                description = "[Playback] Add artificial timing jitter (±1ms), which emulates playback in Note Block Studio and makes the song sound more natural",
                placeholder = "<true|false>",
                arity = 1,
                order = 21)
        public boolean timingJitter = true;

        @Parameter(names = "--sounds-dir",
                description = "[Playback] The folder to load custom instrument sounds from. Default: the Note Block Studio sounds folder (~/Minecraft Note Block Studio/Data/Sounds)",
                placeholder = "<folder>",
                order = 22)
        public File soundsDir;

        // ---------- Renderer ----------
        @Parameter(names = "--max-sounds",
                description = "[Renderer] The maximum amount of sounds playing at the same time, at least 1. Default: 16384",
                placeholder = "<n>",
                order = 30)
        public Integer maxSounds;

        @Parameter(names = "--limiter",
                description = "[Renderer] How to prevent clipping. windowed: adjust the volume over time, global: scale the whole song once, none: no limiting. Default: windowed",
                placeholder = "<windowed|global|none>",
                order = 31)
        public String limiter;

        @Parameter(names = "--multithreaded",
                description = "[Renderer] Render the song using multiple threads",
                order = 32)
        public boolean multithreaded = false;
    }

    @Parameters(commandNames = "play",
            commandDescription = "Play a song")
    public static class Play extends Command {
        // ---------- Input ----------
        @Parameter(names = {"-i", "--input"},
                description = "The song file to play. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs>",
                order = 0)
        public File inputFile;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 1)
        public String inputFormat;

        @Parameter(names = {"-q", "--quiet"},
                description = "Don't print the song information and playback status",
                order = 2)
        public boolean quiet = false;

        // ---------- Playback ----------
        @Parameter(names = "--start",
                description = "[Playback] The position to start playing at, as [hh:]mm:ss or a tick number. Default: the beginning of the song",
                placeholder = "<time|tick>",
                order = 10)
        public String start;

        @Parameter(names = "--volume",
                description = "[Playback] The master volume in percent, 0-100. Default: 90",
                placeholder = "<percent>",
                order = 11)
        public int volume = 90;

        @Parameter(names = "--timing-jitter",
                description = "[Playback] Add artificial timing jitter (±1ms), which emulates playback in Note Block Studio and makes the song sound more natural",
                placeholder = "<true|false>",
                arity = 1,
                order = 12)
        public boolean timingJitter = true;

        @Parameter(names = "--sounds-dir",
                description = "[Playback] The folder to load custom instrument sounds from",
                placeholder = "<folder>",
                order = 13)
        public File soundsDir;

        // ---------- Renderer ----------
        @Parameter(names = "--max-sounds",
                description = "[Renderer] The maximum amount of sounds playing at the same time, at least 1. Default: 4096",
                placeholder = "<n>",
                order = 20)
        public int maxSounds = 4096;

        @Parameter(names = "--multithreaded",
                description = "[Renderer] Render the song using multiple threads",
                order = 21)
        public boolean multithreaded = false;
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
