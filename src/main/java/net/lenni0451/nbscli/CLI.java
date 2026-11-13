package net.lenni0451.nbscli;

import com.beust.jcommander.*;
import com.beust.jcommander.converters.IParameterSplitter;
import net.lenni0451.nbscli.model.AudioFileFormat;
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

    public static class BatchOptions {
        @Parameter(names = {"-r", "--recursive"},
                description = "Also search the sub folders of the given input folders. The folder structure is kept when writing to an output folder",
                order = 7)
        public boolean recursive = false;

        @Parameter(names = "--fail-fast",
                description = "Stop on the first file that fails instead of continuing with the next one",
                order = 8)
        public boolean failFast = false;

        @Parameter(names = "--ignore-extension",
                description = "Read all files found in input folders, not only the ones with a supported file extension. Requires --input-format if the command has it",
                order = 9)
        public boolean ignoreExtension = false;
    }

    public abstract static class Command {
        @Parameter(names = {"-h", "--help"},
                description = "Show the help message of this command",
                help = true,
                order = 1000)
        public boolean help;
    }

    @Parameters(commandNames = "info",
            commandDescription = "Get information about one or more NBS files")
    public static class Info extends Command {
        @Parameter(names = {"-i", "--input"},
                description = "The NBS files or folders to get information about. Folders are searched for .nbs files. Use - to read from standard input",
                required = true,
                placeholder = "<file.nbs|folder>...",
                variableArity = true,
                order = 0)
        public List<File> files;

        @Parameter(names = {"-f", "--full"},
                description = "Show full information about the NBS files",
                order = 1)
        public boolean full = false;

        @ParametersDelegate
        public BatchOptions batch = new BatchOptions();
    }

    @Parameters(commandNames = "convert",
            commandDescription = "Convert one or more song files to a different format")
    public static class Convert extends Command {
        @Parameter(names = {"-i", "--input"},
                description = "The song files or folders to convert. Folders are searched for files with a supported extension (or only the --input-format if specified). Use - to read from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.mid|folder>...",
                variableArity = true,
                order = 0)
        public List<File> inputFiles;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 1)
        public String inputFormat;

        @Parameter(names = {"-o", "--output"},
                description = "The output file to write the converted song to. The format is determined by the file extension, unless the --format option is specified. When converting multiple files or folders, this is the folder to write the converted songs to. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs|folder>",
                order = 2)
        public File outputFile;

        @Parameter(names = {"-f", "-of", "--format", "--output-format"},
                description = "The format to convert the song to. Required if the file extension can't be used to determine the format, when writing to standard output, or when converting multiple files or folders. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 3)
        public String format;

        @Parameter(names = {"-w", "--overwrite"},
                description = "Overwrite the output files if they already exist",
                order = 4)
        public boolean overwrite = false;

        @ParametersDelegate
        public BatchOptions batch = new BatchOptions();
    }

    @Parameters(commandNames = "edit",
            commandDescription = "Edit one or more song files. Edits are applied in the order: resampling, instruments, custom instruments, notes, metadata. The song keeps its format, use the convert command to change it")
    public static class Edit extends Command {
        // ---------- Input/Output ----------
        @Parameter(names = {"-i", "--input"},
                description = "The song files or folders to edit. Folders are searched for files with a supported extension (or only the --input-format if specified). Use - to read from standard input. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs|folder>...",
                variableArity = true,
                order = 0)
        public List<File> inputFiles;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 1)
        public String inputFormat;

        @Parameter(names = {"-o", "--output"},
                description = "The file to write the edited song to. The song is written in the same format as the input. May be the same file as the input when used with --overwrite. When editing multiple files or folders, this is the folder to write the edited songs to (may be the input folder when used with --overwrite)",
                required = true,
                placeholder = "<file.nbs|folder>",
                order = 2)
        public File outputFile;

        @Parameter(names = {"-w", "--overwrite"},
                description = "Overwrite the output files if they already exist",
                order = 3)
        public boolean overwrite = false;

        @ParametersDelegate
        public BatchOptions batch = new BatchOptions();

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
                description = "[Instruments] Replace a custom instrument with a vanilla one or remove it (NBS only). Can be specified multiple times. The custom instrument is selected by its index (as shown by 'info --full') or its name. When editing multiple files, selecting by name is usually the better choice",
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
            commandDescription = "Render one or more songs to audio files. Use the convert command to export to other song formats")
    public static class Render extends Command {
        // ---------- Input/Output ----------
        @Parameter(names = {"-i", "--input"},
                description = "The song files or folders to render. Folders are searched for files with a supported extension (or only the --input-format if specified). Use - to read from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                required = true,
                placeholder = "<file.nbs|folder>...",
                variableArity = true,
                order = 0)
        public List<File> inputFiles;

        @Parameter(names = {"-if", "--input-format"},
                description = "The format of the input song. Required when reading from standard input. Supported formats are: " + ConverterFormats.INPUT_FORMATS_LIST,
                placeholder = "<format>",
                order = 1)
        public String inputFormat;

        @Parameter(names = {"-o", "--output"},
                description = "The audio file to write. The format is determined by the file extension, unless the --format option is specified. When writing to standard output, FLAC and MP3 files don't contain the song length, so some players can't show the duration. PCM (.pcm/.raw) writes the raw samples without any header, e.g. for piping into other programs. When rendering multiple files or folders, this is the folder to write the audio files to",
                required = true,
                placeholder = "<file.wav|folder>",
                order = 2)
        public File outputFile;

        @Parameter(names = {"-f", "-of", "--format", "--output-format"},
                description = "The audio format to render to. Required if the file extension can't be used to determine the format, when writing to standard output, or when rendering multiple files or folders. Supported formats are: " + AudioFileFormat.FORMATS_LIST,
                placeholder = "<format>",
                order = 3)
        public String format;

        @Parameter(names = {"-w", "--overwrite"},
                description = "Overwrite the output files if they already exist",
                order = 4)
        public boolean overwrite = false;

        @Parameter(names = {"-q", "--quiet"},
                description = "Don't print the render progress",
                order = 5)
        public boolean quiet = false;

        @Parameter(names = "--stream",
                description = "Write the audio while rendering instead of rendering the whole song first, e.g. to pipe it into a player. Also uses less memory. Not supported with WAV and the global limiter",
                order = 6)
        public boolean stream = false;

        @ParametersDelegate
        public BatchOptions batch = new BatchOptions();

        // ---------- Audio ----------
        @Parameter(names = "--sample-rate",
                description = "[Audio] The sample rate in Hz. Opus only supports 8000, 12000, 16000, 24000 and 48000, other rates are resampled to the next higher one",
                placeholder = "<hz>",
                order = 10)
        public int sampleRate = 48_000;

        @Parameter(names = "--channels",
                description = "[Audio] The channel layout",
                placeholder = "<mono|stereo>",
                order = 11)
        public String channels = "stereo";

        @Parameter(names = "--pcm-encoding",
                description = "[Audio] The sample encoding (WAV, PCM). u8: unsigned 8-bit, s16/s24/s32: signed 16/24/32-bit, f32: 32-bit float. Multi-byte encodings are little-endian",
                placeholder = "<u8|s16|s24|s32|f32>",
                order = 12)
        public String pcmEncoding = "s16";

        @Parameter(names = "--flac-bits",
                description = "[Audio] The bits per sample, 4-32 (FLAC)",
                placeholder = "<bits>",
                order = 13)
        public int flacBitsPerSample = 16;

        @Parameter(names = "--flac-compression",
                description = "[Audio] The compression level, 0 (fastest) - 8 (smallest) (FLAC)",
                placeholder = "<level>",
                order = 14)
        public int flacCompressionLevel = 6;

        @Parameter(names = "--bitrate-mode",
                description = "[Audio] The bitrate mode (MP3, Opus). cbr: constant, abr: average (constrained variable for Opus), vbr: variable",
                placeholder = "<cbr|abr|vbr>",
                order = 15)
        public String bitrateMode = "vbr";

        @Parameter(names = "--bitrate",
                description = "[Audio] The (target) bitrate in kbps. MP3: 8-320, only with cbr/abr (use --mp3-quality for vbr), default 224. Opus: 6-512, default 128",
                placeholder = "<kbps>",
                order = 16)
        public Integer bitrate;

        @Parameter(names = "--mp3-quality",
                description = "[Audio] The quality in percent, 0-100 (MP3 with vbr)",
                placeholder = "<percent>",
                order = 17)
        public int mp3Quality = 80;

        // ---------- Playback ----------
        @Parameter(names = "--volume",
                description = "[Playback] The master volume in percent, 0-100",
                placeholder = "<percent>",
                order = 20)
        public int volume = 90;

        @Parameter(names = "--timing-jitter",
                description = "[Playback] Add artificial timing jitter (±1ms), which emulates playback in Note Block Studio and makes the song sound more natural",
                placeholder = "<true|false>",
                arity = 1,
                order = 21)
        public boolean timingJitter = true;

        @Parameter(names = "--sounds-dir",
                description = "[Playback] An additional folder to load instrument sounds from, e.g. for custom instruments. It is searched before the Note Block Studio sounds folder",
                placeholder = "<folder>",
                order = 22)
        public File soundsDir;

        // ---------- Renderer ----------
        @Parameter(names = "--max-sounds",
                description = "[Renderer] The maximum amount of sounds playing at the same time, at least 1",
                placeholder = "<n>",
                order = 30)
        public int maxSounds = 1024 * 16;

        @Parameter(names = "--limiter",
                description = "[Renderer] How to prevent clipping. windowed: adjust the volume over time, global: scale the whole song once, none: no limiting",
                placeholder = "<windowed|global|none>",
                order = 31)
        public String limiter = "windowed";

        @Parameter(names = "--multithreaded",
                description = "[Renderer] Render the song using multiple threads",
                order = 32)
        public boolean multithreaded = false;

        @Parameter(names = "--pre-render",
                description = "[Renderer] The memory in MiB used to pre-render the most used notes, which speeds up rendering. 0 disables pre-rendering. Default: 512, or 0 with --multithreaded",
                placeholder = "<mib>",
                order = 33)
        public Integer preRenderMemory;

        @Parameter(names = "--resampler",
                description = "[Renderer] The resampler used to change the pitch of the instrument sounds. point: fastest, lowest quality, linear, cubic, sinc: slowest, highest quality",
                placeholder = "<point|linear|cubic|sinc>",
                order = 34)
        public String resampler = "linear";
    }

    @Parameters(commandNames = "play",
            commandDescription = "Play a song. Controls: space to pause/resume, left/right to seek, up/down to change the volume, q to quit")
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
                description = "Don't show any playback output",
                order = 2)
        public boolean quiet = false;

        @Parameter(names = "--simple",
                description = "Show a simple progress line instead of the player UI. Used automatically when the terminal is not interactive",
                order = 3)
        public boolean simple = false;

        // ---------- Audio ----------
        @Parameter(names = "--sample-rate",
                description = "[Audio] The sample rate in Hz",
                placeholder = "<hz>",
                order = 10)
        public int sampleRate = 48_000;

        @Parameter(names = "--channels",
                description = "[Audio] The channel layout",
                placeholder = "<mono|stereo>",
                order = 11)
        public String channels = "stereo";

        // ---------- Playback ----------
        @Parameter(names = "--start",
                description = "[Playback] The position to start playing at, as [hh:]mm:ss or a tick number. Default: the beginning of the song",
                placeholder = "<time|tick>",
                order = 20)
        public String start;

        @Parameter(names = "--volume",
                description = "[Playback] The master volume in percent, 0-100",
                placeholder = "<percent>",
                order = 21)
        public int volume = 90;

        @Parameter(names = "--timing-jitter",
                description = "[Playback] Add artificial timing jitter (±1ms), which emulates playback in Note Block Studio and makes the song sound more natural",
                placeholder = "<true|false>",
                arity = 1,
                order = 22)
        public boolean timingJitter = true;

        @Parameter(names = "--sounds-dir",
                description = "[Playback] An additional folder to load instrument sounds from, e.g. for custom instruments. It is searched before the Note Block Studio sounds folder",
                placeholder = "<folder>",
                order = 23)
        public File soundsDir;

        // ---------- Renderer ----------
        @Parameter(names = "--max-sounds",
                description = "[Renderer] The maximum amount of sounds playing at the same time, at least 1",
                placeholder = "<n>",
                order = 30)
        public int maxSounds = 4096;

        @Parameter(names = "--multithreaded",
                description = "[Renderer] Render the song using multiple threads",
                order = 31)
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
