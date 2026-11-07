package net.lenni0451.nbscli.model;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.utils.IOUtils;

import java.io.File;
import java.util.Locale;

public enum AudioFileFormat {

    WAV,
    PCM,
    FLAC,
    MP3,
    OPUS;

    public static final String FORMATS_LIST = "WAV, PCM, FLAC, MP3, Opus";

    public static AudioFileFormat parse(final String cliName, final File file) {
        String formatName;
        if (cliName != null) {
            formatName = cliName;
        } else if (!IOUtils.isStd(file)) {
            formatName = IOUtils.getExtension(file);
        } else {
            throw new ParameterException("Output format must be specified when using standard output");
        }
        if (formatName.equalsIgnoreCase("raw")) return PCM;
        for (AudioFileFormat format : values()) {
            if (format.name().equalsIgnoreCase(formatName)) {
                return format;
            }
        }
        throw new ParameterException("Invalid output format: " + formatName + ". Supported formats are: " + FORMATS_LIST);
    }

    @Override
    public String toString() {
        return this == OPUS ? "Opus" : this.name().toUpperCase(Locale.ROOT);
    }

}
