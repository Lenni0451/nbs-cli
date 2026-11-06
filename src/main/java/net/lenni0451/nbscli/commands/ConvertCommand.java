package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.utils.IOUtils;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;
import net.raphimc.noteblocklib.model.note.Note;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblocklib.util.MinecraftOctaveCorrection;
import net.raphimc.noteblocklib.util.SongUtil;

import java.io.File;
import java.util.Locale;
import java.util.Map;

public class ConvertCommand {

    private final File input;
    private final File output;
    private final SongFormat inputFormat;
    private final SongFormat targetFormat;

    public ConvertCommand(final CLI.Convert convert) {
        if (!IOUtils.isStd(convert.inputFile) && !convert.inputFile.isFile()) {
            throw new ParameterException("Input file does not exist or is not a file: " + convert.inputFile.getAbsolutePath());
        }
        if (!IOUtils.isStd(convert.outputFile) && convert.outputFile.exists() && !convert.overwrite) {
            throw new ParameterException("Output file already exists and overwrite is not allowed: " + convert.outputFile.getAbsolutePath());
        }
        if (!IOUtils.isStd(convert.outputFile) && convert.outputFile.isDirectory()) {
            throw new ParameterException("Output file is a directory: " + convert.outputFile.getAbsolutePath());
        }
        this.input = convert.inputFile;
        this.output = convert.outputFile;
        this.inputFormat = this.parseFormat(convert.inputFormat, this.input, "input", ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST);
        this.targetFormat = this.parseFormat(convert.format, this.output, "output", ConverterFormats.OUTPUT_FORMATS, ConverterFormats.OUTPUT_FORMATS_LIST);
    }

    private SongFormat parseFormat(final String cliName, final File file, final String io, final Map<String, SongFormat> formats, final String formatsList) {
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

    public void execute() {
        Song song;
        try {
            String songName = IOUtils.isStd(this.input) ? null : IOUtils.getNameWithoutExtension(this.input);
            song = NoteBlockLib.readSong(IOUtils.openInputStream(this.input), this.inputFormat, songName);
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
            NoteBlockLib.writeSong(song, IOUtils.openOutputStream(this.output));
            this.log("Successfully converted " + IOUtils.getName(this.input, "STDIN") + " from " + this.inputFormat.name() + " to " + IOUtils.getName(this.output, "STDOUT") + " in " + this.targetFormat.name() + " format");
        } catch (Throwable t) {
            throw new ParameterException("Failed to write output file: " + t.getMessage(), t);
        }
    }

    private void fixKeys(final Song song) {
        boolean needsFixing = song.getNotes().testEach(Note::isOutsideMinecraftOctaveRange);
        if (needsFixing) {
            this.log("Song contains notes outside the Minecraft octave range");
            this.log("The format you are converting to does not support these notes");
            this.log("Instrument shift will be applied to bring all notes into the valid range");
            this.log("If you want a different behavior, edit the song before converting it");

            MinecraftOctaveCorrection.INSTRUMENT_SHIFT.apply(song);
        }
    }

    private void fixInstruments(final Song song) {
        boolean needsFixing = SongUtil.getUsedVanillaInstruments(song).stream().anyMatch(instrument -> !this.isInstrumentSupportedByMCSP2(instrument));
        if (needsFixing) {
            this.log("Song contains instruments that are not supported by the MCSP2 format");
            this.log("These instruments will be removed from the song");

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

    private void log(final String message) {
        if (IOUtils.isStd(this.output)) {
            System.err.println(message);
        } else {
            System.out.println(message);
        }
    }

}
