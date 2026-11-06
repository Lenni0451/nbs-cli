package net.lenni0451.nbscli.model;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.utils.IOUtils;
import net.raphimc.noteblocklib.format.SongFormat;

import java.io.File;
import java.util.Locale;
import java.util.Map;

public class ConverterFormats {

    public static final String INPUT_FORMATS_LIST = "NBS, MCSP, MCSP2, TXT, MIDI";
    public static final Map<String, SongFormat> INPUT_FORMATS_MAP = Map.of(
            "nbs", SongFormat.NBS,
            "mcsp", SongFormat.MCSP,
            "mcsp2", SongFormat.MCSP2,
            "txt", SongFormat.TXT,
            "mid", SongFormat.MIDI,
            "midi", SongFormat.MIDI
    );

    public static final String OUTPUT_FORMATS_LIST = "NBS, MCSP2, TXT";
    public static final Map<String, SongFormat> OUTPUT_FORMATS = Map.of(
            "nbs", SongFormat.NBS,
            "mcsp2", SongFormat.MCSP2,
            "txt", SongFormat.TXT
    );

    public static SongFormat parse(final String cliName, final File file, final String io, final Map<String, SongFormat> formats, final String formatsList) {
        String formatName;
        if (cliName != null) {
            formatName = cliName;
        } else if (!IOUtils.isStd(file)) {
            formatName = IOUtils.getExtension(file);
        } else {
            throw new ParameterException(io.substring(0, 1).toUpperCase(Locale.ROOT) + io.substring(1) + " format must be specified when using standard " + io);
        }
        SongFormat format = formats.get(formatName.toLowerCase(Locale.ROOT));
        if (format == null) {
            throw new ParameterException("Invalid " + io + " format: " + formatName + ". Supported formats are: " + formatsList);
        }
        return format;
    }

}
