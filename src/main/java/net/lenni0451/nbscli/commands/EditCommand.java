package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.model.InputFile;
import net.lenni0451.nbscli.model.InstrumentNames;
import net.lenni0451.nbscli.utils.BatchProcessor;
import net.lenni0451.nbscli.utils.IOUtils;
import net.lenni0451.nbscli.utils.SongFixer;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.mcsp2.model.McSp2Song;
import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;
import net.raphimc.noteblocklib.format.nbs.model.NbsCustomInstrument;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
import net.raphimc.noteblocklib.model.note.Instrument;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblocklib.util.MinecraftOctaveCorrection;
import net.raphimc.noteblocklib.util.SongResampler;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EditCommand {

    private static final String REMOVE = "remove";

    private final List<Job> jobs = new ArrayList<>();
    private final CLI.Edit edit;

    // Vanilla instrument -> replacement, null value means remove
    private final Map<MinecraftInstrument, MinecraftInstrument> instrumentMappings = new EnumMap<>(MinecraftInstrument.class);
    // Custom instrument selector (index or name) -> replacement, null value means remove. Resolved after the song is loaded
    private final Map<String, MinecraftInstrument> customInstrumentMappings = new LinkedHashMap<>();
    // The metadata edits for each input format, as the supported options depend on the format
    private final Map<SongFormat, List<Consumer<Song>>> metadataEdits = new EnumMap<>(SongFormat.class);

    public EditCommand(final CLI.Edit edit) {
        List<InputFile> inputs = IOUtils.resolveInputs(edit.inputFiles, edit.batch.recursive, ConverterFormats.folderFilter(edit.inputFormat, edit.batch.ignoreExtension, ConverterFormats.OUTPUT_FORMATS, ConverterFormats.OUTPUT_FORMATS_LIST));
        if (inputs.isEmpty()) {
            throw new ParameterException("No supported song files found in the given folders");
        }
        boolean batch = IOUtils.isBatch(edit.inputFiles);
        if (batch) IOUtils.validateOutputFolder(edit.outputFile);
        Map<InputFile, File> outputs = new LinkedHashMap<>();
        for (InputFile input : inputs) {
            SongFormat format = ConverterFormats.parse(edit.inputFormat, input.file(), "input", ConverterFormats.OUTPUT_FORMATS, ConverterFormats.OUTPUT_FORMATS_LIST);
            File output = batch ? input.getOutputFile(edit.outputFile, null) : edit.outputFile;
            outputs.put(input, output);
            this.jobs.add(new Job(input, format, output));
        }
        IOUtils.validateOutputFiles(outputs, edit.overwrite);
        this.edit = edit;

        if (edit.tempo != null && !(edit.tempo > 0 && Float.isFinite(edit.tempo))) {
            throw new ParameterException("Tempo must be greater than 0: " + edit.tempo);
        }
        if (edit.minVolume != null && (edit.minVolume < 0 || edit.minVolume > 100)) {
            throw new ParameterException("Minimum volume must be between 0 and 100: " + edit.minVolume);
        }
        for (String mapping : edit.instruments) {
            String[] parts = this.splitMapping("--instrument", mapping);
            MinecraftInstrument from = InstrumentNames.parse(parts[0]);
            if (this.instrumentMappings.containsKey(from)) {
                throw new ParameterException("Instrument " + InstrumentNames.format(from) + " is mapped more than once");
            }
            this.instrumentMappings.put(from, this.parseTarget(parts[1]));
        }
        if (!edit.customInstruments.isEmpty() && this.jobs.stream().anyMatch(job -> !job.format().equals(SongFormat.NBS))) {
            throw new ParameterException("--custom-instrument is only supported for NBS songs");
        }
        for (String mapping : edit.customInstruments) {
            String[] parts = this.splitMapping("--custom-instrument", mapping);
            if (this.customInstrumentMappings.containsKey(parts[0])) {
                throw new ParameterException("Custom instrument " + parts[0] + " is mapped more than once");
            }
            this.customInstrumentMappings.put(parts[0], this.parseTarget(parts[1]));
        }
        for (Job job : this.jobs) {
            this.metadataEdits.computeIfAbsent(job.format(), this::collectMetadataEdits);
        }
        boolean hasMetadataEdits = this.metadataEdits.values().stream().anyMatch(edits -> !edits.isEmpty());

        if (edit.tempo == null && !edit.precomputeTempo && this.instrumentMappings.isEmpty() && this.customInstrumentMappings.isEmpty()
                && edit.octaveCorrection == null && edit.minVolume == null && !edit.removeDuplicates && !hasMetadataEdits) {
            throw new ParameterException("No edits specified. Use --help to see all available edit options");
        }
    }

    public void execute() {
        BatchProcessor.run(this.jobs, this.edit.batch.failFast, true, job -> job.input().name(), this::edit);
    }

    private void edit(final Job job) {
        File input = job.input().file();
        List<Consumer<Song>> metadataEdits = this.metadataEdits.get(job.format());
        Song song;
        try {
            String songName = IOUtils.isStd(input) ? null : IOUtils.getNameWithoutExtension(input);
            song = NoteBlockLib.readSong(IOUtils.openInputStream(input), job.format(), songName);
        } catch (Throwable t) {
            throw new ParameterException("Failed to read input file: " + t.getMessage(), t);
        }

        if (!this.hasNoteEdits()) {
            // Rebuilding the song loses NBS specific data like layer names, so only do it when the notes were changed
            metadataEdits.forEach(metadataEdit -> metadataEdit.accept(song));
            this.write(job, song);
            return;
        }

        // Resampling
        if (this.edit.precomputeTempo) {
            SongResampler.precomputeTempoEvents(song);
        }
        if (this.edit.tempo != null) {
            SongResampler.changeTickSpeed(song, this.edit.tempo);
        }

        // Instruments
        this.applyInstrumentMappings(song, this.instrumentMappings);
        if (!this.customInstrumentMappings.isEmpty()) {
            this.applyInstrumentMappings(song, this.resolveCustomInstruments((NbsSong) song));
        }

        // Notes
        if (this.edit.octaveCorrection != null && this.edit.octaveCorrection != MinecraftOctaveCorrection.NONE) {
            this.edit.octaveCorrection.apply(song);
        }
        if (this.edit.minVolume != null) {
            song.getNotes().removeSilentNotes(this.edit.minVolume / 100F);
        }
        if (this.edit.removeDuplicates) {
            song.getNotes().removeDoubleNotes();
        }

        // Metadata
        metadataEdits.forEach(metadataEdit -> metadataEdit.accept(song));

        SongFixer.fixForFormat(song, job.format(), message -> IOUtils.log(job.output(), message));
        this.write(job, NoteBlockLib.convertSong(song, job.format()));
    }

    private boolean hasNoteEdits() {
        return this.edit.tempo != null || this.edit.precomputeTempo || !this.instrumentMappings.isEmpty() || !this.customInstrumentMappings.isEmpty()
                || (this.edit.octaveCorrection != null && this.edit.octaveCorrection != MinecraftOctaveCorrection.NONE)
                || this.edit.minVolume != null || this.edit.removeDuplicates;
    }

    private void write(final Job job, final Song song) {
        try {
            NoteBlockLib.writeSong(song, IOUtils.openOutputStream(job.output()));
            IOUtils.log(job.output(), "Successfully edited " + IOUtils.getName(job.input().file(), "STDIN") + " and saved it to " + IOUtils.getName(job.output(), "STDOUT"));
        } catch (Throwable t) {
            throw new ParameterException("Failed to write output file: " + t.getMessage(), t);
        }
    }

    private String[] splitMapping(final String option, final String mapping) {
        int index = mapping.lastIndexOf('=');
        if (index <= 0 || index == mapping.length() - 1) {
            throw new ParameterException("Invalid value for " + option + ": " + mapping + ". Expected <from>=<to|remove>");
        }
        return new String[]{mapping.substring(0, index).trim(), mapping.substring(index + 1).trim()};
    }

    private MinecraftInstrument parseTarget(final String target) {
        if (target.equalsIgnoreCase(REMOVE)) return null;
        return InstrumentNames.parse(target);
    }

    private Map<NbsCustomInstrument, MinecraftInstrument> resolveCustomInstruments(final NbsSong song) {
        List<NbsCustomInstrument> customInstruments = song.getCustomInstruments();
        Map<NbsCustomInstrument, MinecraftInstrument> resolved = new HashMap<>();
        for (Map.Entry<String, MinecraftInstrument> entry : this.customInstrumentMappings.entrySet()) {
            NbsCustomInstrument instrument = this.findCustomInstrument(customInstruments, entry.getKey());
            if (resolved.containsKey(instrument)) {
                throw new ParameterException("Custom instrument " + entry.getKey() + " is mapped more than once");
            }
            resolved.put(instrument, entry.getValue());
        }
        return resolved;
    }

    private NbsCustomInstrument findCustomInstrument(final List<NbsCustomInstrument> customInstruments, final String selector) {
        if (selector.chars().allMatch(Character::isDigit)) {
            int index = Integer.parseInt(selector);
            if (index >= customInstruments.size()) {
                throw new ParameterException("Custom instrument index " + index + " is out of range. The song has " + customInstruments.size() + " custom instrument(s)");
            }
            return customInstruments.get(index);
        }
        List<NbsCustomInstrument> matches = customInstruments.stream()
                .filter(instrument -> instrument.getName() != null && instrument.getName().equalsIgnoreCase(selector))
                .toList();
        if (matches.isEmpty()) {
            throw new ParameterException("The song has no custom instrument named " + selector);
        }
        if (matches.size() > 1) {
            throw new ParameterException("Multiple custom instruments are named " + selector + ". Use the index instead (see 'info --full')");
        }
        return matches.get(0);
    }

    private void applyInstrumentMappings(final Song song, final Map<? extends Instrument, MinecraftInstrument> mappings) {
        if (mappings.isEmpty()) return;
        Set<Instrument> remove = new HashSet<>();
        mappings.forEach((from, to) -> {
            if (to == null) remove.add(from);
        });
        song.getNotes().removeIf(note -> remove.contains(note.getInstrument()));
        song.getNotes().forEach(note -> {
            MinecraftInstrument replacement = mappings.get(note.getInstrument());
            if (replacement != null) {
                note.setInstrument(replacement);
            }
        });
    }

    private List<Consumer<Song>> collectMetadataEdits(final SongFormat format) {
        List<Consumer<Song>> edits = new ArrayList<>();
        this.string(edits, format, "--title", this.edit.title, Song::setTitle, Song::setTitle);
        this.string(edits, format, "--author", this.edit.author, Song::setAuthor, Song::setAuthor);
        this.string(edits, format, "--original-author", this.edit.originalAuthor, Song::setOriginalAuthor, Song::setOriginalAuthor);
        this.string(edits, format, "--description", this.edit.description, Song::setDescription, null);
        this.string(edits, format, "--source-file", this.edit.sourceFileName, NbsSong::setSourceFileName, null);
        this.bool(edits, format, "--auto-save", this.edit.autoSave, NbsSong::setAutoSave);
        this.integer(edits, format, "--auto-save-interval", this.edit.autoSaveInterval, 0, 255, NbsSong::setAutoSaveInterval, McSp2Song::setAutoSaveInterval);
        this.integer(edits, format, "--time-signature", this.edit.timeSignature, 0, 255, NbsSong::setTimeSignature, null);
        this.integer(edits, format, "--minutes-spent", this.edit.minutesSpent, 0, Integer.MAX_VALUE, NbsSong::setMinutesSpent, McSp2Song::setMinutesSpent);
        this.integer(edits, format, "--left-clicks", this.edit.leftClicks, 0, Integer.MAX_VALUE, NbsSong::setLeftClicks, McSp2Song::setLeftClicks);
        this.integer(edits, format, "--right-clicks", this.edit.rightClicks, 0, Integer.MAX_VALUE, NbsSong::setRightClicks, McSp2Song::setRightClicks);
        this.integer(edits, format, "--note-blocks-added", this.edit.noteBlocksAdded, 0, Integer.MAX_VALUE, NbsSong::setNoteBlocksAdded, McSp2Song::setNoteBlocksAdded);
        this.integer(edits, format, "--note-blocks-removed", this.edit.noteBlocksRemoved, 0, Integer.MAX_VALUE, NbsSong::setNoteBlocksRemoved, McSp2Song::setNoteBlocksRemoved);
        this.bool(edits, format, "--loop", this.edit.loop, NbsSong::setLoop);
        this.integer(edits, format, "--max-loop-count", this.edit.maxLoopCount, 0, 255, NbsSong::setMaxLoopCount, null);
        this.integer(edits, format, "--loop-start-tick", this.edit.loopStartTick, 0, Short.MAX_VALUE, (song, value) -> song.setLoopStartTick((short) (int) value), null);
        return edits;
    }

    private void string(final List<Consumer<Song>> edits, final SongFormat format, final String option, final String value, final BiConsumer<NbsSong, String> nbs, final BiConsumer<McSp2Song, String> mcsp2) {
        if (value == null) return;
        this.metadata(edits, format, option, nbs == null ? null : song -> nbs.accept(song, value), mcsp2 == null ? null : song -> mcsp2.accept(song, value));
    }

    private void bool(final List<Consumer<Song>> edits, final SongFormat format, final String option, final Boolean value, final BiConsumer<NbsSong, Boolean> nbs) {
        if (value == null) return;
        this.metadata(edits, format, option, song -> nbs.accept(song, value), null);
    }

    private void integer(final List<Consumer<Song>> edits, final SongFormat format, final String option, final Integer value, final int min, final int max, final BiConsumer<NbsSong, Integer> nbs, final BiConsumer<McSp2Song, Integer> mcsp2) {
        if (value == null) return;
        if (value < min || value > max) {
            throw new ParameterException(option + " must be between " + min + " and " + max + ": " + value);
        }
        this.metadata(edits, format, option, nbs == null ? null : song -> nbs.accept(song, value), mcsp2 == null ? null : song -> mcsp2.accept(song, value));
    }

    private void metadata(final List<Consumer<Song>> edits, final SongFormat format, final String option, final Consumer<NbsSong> nbs, final Consumer<McSp2Song> mcsp2) {
        if (format.equals(SongFormat.NBS) && nbs != null) {
            edits.add(song -> nbs.accept((NbsSong) song));
        } else if (format.equals(SongFormat.MCSP2) && mcsp2 != null) {
            edits.add(song -> mcsp2.accept((McSp2Song) song));
        } else {
            throw new ParameterException(option + " is not supported for " + format.name().toUpperCase(Locale.ROOT) + " songs");
        }
    }


    private record Job(InputFile input, SongFormat format, File output) {
    }

}
