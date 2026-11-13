package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.model.InputFile;
import net.lenni0451.nbscli.utils.BatchProcessor;
import net.lenni0451.nbscli.utils.IOUtils;
import net.lenni0451.nbscli.utils.SongFixer;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.model.song.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConvertCommand {

    private final List<Job> jobs = new ArrayList<>();
    private final SongFormat targetFormat;
    private final boolean failFast;

    public ConvertCommand(final CLI.Convert convert) {
        List<InputFile> inputs = IOUtils.resolveInputs(convert.inputFiles, convert.batch.recursive, ConverterFormats.folderFilter(convert.inputFormat, convert.batch.ignoreExtension, ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST));
        if (inputs.isEmpty()) {
            throw new ParameterException("No supported song files found in the given folders");
        }
        boolean batch = IOUtils.isBatch(convert.inputFiles);
        if (batch) {
            IOUtils.validateOutputFolder(convert.outputFile);
            if (convert.format == null) {
                throw new ParameterException("The output format must be specified with --format when converting multiple files or folders");
            }
        }
        this.targetFormat = ConverterFormats.parse(convert.format, convert.outputFile, "output", ConverterFormats.OUTPUT_FORMATS, ConverterFormats.OUTPUT_FORMATS_LIST);
        this.failFast = convert.batch.failFast;

        Map<InputFile, File> outputs = new LinkedHashMap<>();
        for (InputFile input : inputs) {
            SongFormat inputFormat = ConverterFormats.parse(convert.inputFormat, input.file(), "input", ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST);
            File output = batch ? input.getOutputFile(convert.outputFile, this.targetFormat.getExtensions().get(0)) : convert.outputFile;
            outputs.put(input, output);
            this.jobs.add(new Job(input, inputFormat, output));
        }
        IOUtils.validateOutputFiles(outputs, convert.overwrite);
    }

    public void execute() {
        BatchProcessor.run(this.jobs, this.failFast, true, job -> job.input().name(), this::convert);
    }

    private void convert(final Job job) {
        File input = job.input().file();
        Song song;
        try {
            String songName = IOUtils.isStd(input) ? null : IOUtils.getNameWithoutExtension(input);
            song = NoteBlockLib.readSong(IOUtils.openInputStream(input), job.inputFormat(), songName);
        } catch (Throwable t) {
            throw new ParameterException("Failed to read input file: " + t.getMessage(), t);
        }
        SongFixer.fixForFormat(song, this.targetFormat, message -> IOUtils.log(job.output(), message));
        song = NoteBlockLib.convertSong(song, this.targetFormat);
        try {
            NoteBlockLib.writeSong(song, IOUtils.openOutputStream(job.output()));
            IOUtils.log(job.output(), "Successfully converted " + IOUtils.getName(input, "STDIN") + " from " + job.inputFormat().name() + " to " + IOUtils.getName(job.output(), "STDOUT") + " in " + this.targetFormat.name() + " format");
        } catch (Throwable t) {
            throw new ParameterException("Failed to write output file: " + t.getMessage(), t);
        }
    }


    private record Job(InputFile input, SongFormat inputFormat, File output) {
    }

}
