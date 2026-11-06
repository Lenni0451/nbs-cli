package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.model.MinecraftOctaveCorrection;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;
import net.raphimc.noteblocklib.model.note.Note;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblocklib.util.SongUtil;

import java.io.File;
import java.util.Locale;

public class ConvertCommand {

    private final File input;
    private final File output;
    private final SongFormat targetFormat;

    public ConvertCommand(final CLI.Convert convert) {
        if (!convert.inputFile.isFile()) {
            throw new ParameterException("Input file does not exist or is not a file: " + convert.inputFile.getAbsolutePath());
        }
        if (convert.outputFile.exists() && !convert.overwrite) {
            throw new ParameterException("Output file already exists and overwrite is not allowed: " + convert.outputFile.getAbsolutePath());
        }
        if (convert.outputFile.isDirectory()) {
            throw new ParameterException("Output file is a directory: " + convert.outputFile.getAbsolutePath());
        }
        this.input = convert.inputFile;
        this.output = convert.outputFile;
        if (convert.format != null) {
            this.targetFormat = ConverterFormats.OUTPUT_FORMATS.get(convert.format.toLowerCase(Locale.ROOT));
        } else {
            String extension = this.output.getName().substring(this.output.getName().lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            this.targetFormat = ConverterFormats.OUTPUT_FORMATS.get(extension);
        }
        if (this.targetFormat == null) {
            throw new ParameterException("Invalid output format: " + convert.format + ". Supported formats are: " + ConverterFormats.OUTPUT_FORMATS_LIST);
        }
    }

    public void execute() {
        Song song;
        try {
            song = NoteBlockLib.readSong(this.input);
        } catch (Throwable t) {
            throw new ParameterException("Failed to read input file: " + t.getMessage(), t);
        }
        song = NoteBlockLib.convertSong(song, this.targetFormat);
        if (this.targetFormat.equals(SongFormat.MCSP2) || this.targetFormat.equals(SongFormat.TXT)) {
            this.fixKeys(song);
        }
        if (this.targetFormat.equals(SongFormat.MCSP2)) {
            this.fixInstruments(song);
        }
        try {
            NoteBlockLib.writeSong(song, this.output);
            System.out.println("Successfully converted " + this.input.getName() + " from " + song.getFormat().name() + " to " + this.output.getName() + " in " + this.targetFormat.name() + " format");
        } catch (Throwable t) {
            throw new ParameterException("Failed to write output file: " + t.getMessage(), t);
        }
    }

    private void fixKeys(final Song song) {
        boolean needsFixing = song.getNotes().testEach(Note::isOutsideMinecraftOctaveRange);
        if (needsFixing) {
            System.out.println("Song contains notes outside the Minecraft octave range");
            System.out.println("The format you are converting to does not support these notes");
            System.out.println("Instrument shift will be applied to bring all notes into the valid range");
            System.out.println("If you want a different behavior, edit the song before converting it");

            MinecraftOctaveCorrection.INSTRUMENT_SHIFT.apply(song);
        }
    }

    private void fixInstruments(final Song song) {
        boolean needsFixing = SongUtil.getUsedVanillaInstruments(song).stream().anyMatch(instrument -> !this.isInstrumentSupportedByMCSP2(instrument));
        if (needsFixing) {
            System.out.println("Song contains instruments that are not supported by the MCSP2 format");
            System.out.println("These instruments will be removed from the song");

            song.getNotes().removeIf(note -> {
                if (note.getInstrument() instanceof MinecraftInstrument instrument) {
                    return !this.isInstrumentSupportedByMCSP2(instrument);
                }
                return true; // Custom instruments are also not supported
            });
        }
    }

    private boolean isInstrumentSupportedByMCSP2(final MinecraftInstrument instrument) {
        return instrument.nbsId() >= 0 && instrument.nbsId() <= 4;
    }

}
