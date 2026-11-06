package net.lenni0451.nbscli.utils;

import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;
import net.raphimc.noteblocklib.model.note.Note;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblocklib.util.MinecraftOctaveCorrection;
import net.raphimc.noteblocklib.util.SongUtil;

import java.util.function.Consumer;

public class SongFixer {

    public static void fixForFormat(final Song song, final SongFormat format, final Consumer<String> log) {
        if (format.equals(SongFormat.MCSP2) || format.equals(SongFormat.TXT)) {
            fixKeys(song, log);
        }
        if (format.equals(SongFormat.MCSP2)) {
            fixInstruments(song, log);
        }
    }

    private static void fixKeys(final Song song, final Consumer<String> log) {
        boolean needsFixing = song.getNotes().testEach(Note::isOutsideMinecraftOctaveRange);
        if (needsFixing) {
            log.accept("Song contains notes outside the Minecraft octave range");
            log.accept("The output format does not support these notes");
            log.accept("Instrument shift will be applied to bring all notes into the valid range");
            log.accept("If you want a different behavior, use the --octave-correction option of the edit command");

            MinecraftOctaveCorrection.INSTRUMENT_SHIFT.apply(song);
        }
    }

    private static void fixInstruments(final Song song, final Consumer<String> log) {
        boolean needsFixing = SongUtil.getUsedVanillaInstruments(song).stream().anyMatch(instrument -> !isInstrumentSupportedByMCSP2(instrument));
        if (needsFixing) {
            log.accept("Song contains instruments that are not supported by the MCSP2 format");
            log.accept("These instruments will be removed from the song");

            song.getNotes().removeIf(note -> {
                if (note.getInstrument() instanceof MinecraftInstrument instrument) {
                    return !isInstrumentSupportedByMCSP2(instrument);
                }
                return true; // Custom instruments are also not supported
            });
        }
    }

    private static boolean isInstrumentSupportedByMCSP2(final MinecraftInstrument instrument) {
        return instrument.nbsId() >= 0 && instrument.nbsId() <= 4;
    }

}
