package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.utils.IOUtils;
import net.lenni0451.nbscli.utils.SongFixer;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.model.song.Song;

import java.io.File;

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
        this.inputFormat = ConverterFormats.parse(convert.inputFormat, this.input, "input", ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST);
        this.targetFormat = ConverterFormats.parse(convert.format, this.output, "output", ConverterFormats.OUTPUT_FORMATS, ConverterFormats.OUTPUT_FORMATS_LIST);
    }

    public void execute() {
        Song song;
        try {
            String songName = IOUtils.isStd(this.input) ? null : IOUtils.getNameWithoutExtension(this.input);
            song = NoteBlockLib.readSong(IOUtils.openInputStream(this.input), this.inputFormat, songName);
        } catch (Throwable t) {
            throw new ParameterException("Failed to read input file: " + t.getMessage(), t);
        }
        SongFixer.fixForFormat(song, this.targetFormat, message -> IOUtils.log(this.output, message));
        song = NoteBlockLib.convertSong(song, this.targetFormat);
        try {
            NoteBlockLib.writeSong(song, IOUtils.openOutputStream(this.output));
            IOUtils.log(this.output, "Successfully converted " + IOUtils.getName(this.input, "STDIN") + " from " + this.inputFormat.name() + " to " + IOUtils.getName(this.output, "STDOUT") + " in " + this.targetFormat.name() + " format");
        } catch (Throwable t) {
            throw new ParameterException("Failed to write output file: " + t.getMessage(), t);
        }
    }

}
