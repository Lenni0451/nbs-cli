package net.lenni0451.nbscli.model;

import net.raphimc.noteblocklib.format.SongFormat;

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

}
