package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.AudioFileFormat;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.model.InputFile;
import net.lenni0451.nbscli.utils.*;
import net.raphimc.audiomixer.AudioMixer;
import net.raphimc.audiomixer.io.AudioOutputStream;
import net.raphimc.audiomixer.io.flac.FlacAudioOutputStream;
import net.raphimc.audiomixer.io.mp3.Mp3AudioOutputStream;
import net.raphimc.audiomixer.io.ogg.opus.OggOpusAudioOutputStream;
import net.raphimc.audiomixer.io.pcm.PcmAudioOutputStream;
import net.raphimc.audiomixer.io.wav.pcm.WavPcmAudioOutputStream;
import net.raphimc.audiomixer.processor.dynamics.GainProcessor;
import net.raphimc.audiomixer.processor.dynamics.LimiterProcessor;
import net.raphimc.audiomixer.processor.effect.HardClipProcessor;
import net.raphimc.audiomixer.resampler.Resampler;
import net.raphimc.audiomixer.util.AudioFormat;
import net.raphimc.audiomixer.util.PcmSampleEncoding;
import net.raphimc.audiomixer.util.buffer.AudioBuffer;
import net.raphimc.audiomixer.util.io.seekable.SeekableBufferedOutputStream;
import net.raphimc.audiomixer.util.io.seekable.SeekableFileOutputStream;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.nbs.model.NbsCustomInstrument;
import net.raphimc.noteblocklib.model.note.Instrument;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblockrenderer.AudioMixerNoteBlockRenderer;
import net.raphimc.noteblockrenderer.NoteBlockRenderer;
import net.raphimc.noteblockrenderer.provider.SoundProvider;
import net.raphimc.noteblockrenderer.provider.impl.NoteBlockStudioSoundProvider;
import net.raphimc.noteblockrenderer.provider.impl.UrlSoundProvider;
import net.raphimc.noteblockrenderer.util.MetadataUtil;
import org.concentus.OpusSignal;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Supplier;

public class RenderCommand {

    private static final int DEFAULT_PRE_RENDER_MEMORY = 512;

    private final List<Job> jobs = new ArrayList<>();
    private final AudioFileFormat outputFormat;
    private final AudioFormat audioFormat;
    private final NoteBlockRenderer.Limiter limiter;
    private final Supplier<Resampler> resampler;
    private final int preRenderMemory;
    private final CLI.Render render;
    // Only the encoding of the selected output format is set, the others are null
    private final PcmSampleEncoding pcmEncoding;
    private final FlacAudioOutputStream.Encoding flacEncoding;
    private final Mp3AudioOutputStream.Encoding mp3Encoding;
    private final OggOpusAudioOutputStream.Encoding opusEncoding;

    public RenderCommand(final CLI.Render render) {
        List<InputFile> inputs = IOUtils.resolveInputs(render.inputFiles, render.batch.recursive, ConverterFormats.folderFilter(render.inputFormat, render.batch.ignoreExtension, ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST));
        if (inputs.isEmpty()) {
            throw new ParameterException("No supported song files found in the given folders");
        }
        boolean batch = IOUtils.isBatch(render.inputFiles);
        if (batch) {
            IOUtils.validateOutputFolder(render.outputFile);
            if (render.format == null) {
                throw new ParameterException("The output format must be specified with --format when rendering multiple files or folders");
            }
        }
        if (render.soundsDir != null && !render.soundsDir.isDirectory()) {
            throw new ParameterException("Sounds folder does not exist or is not a folder: " + render.soundsDir.getAbsolutePath());
        }
        if (render.volume < 0 || render.volume > 100) {
            throw new ParameterException("Volume must be between 0 and 100: " + render.volume);
        }
        if (render.maxSounds < 1) {
            throw new ParameterException("Max sounds must be at least 1: " + render.maxSounds);
        }
        if (render.preRenderMemory != null && render.preRenderMemory < 0) {
            throw new ParameterException("Pre-render memory must be at least 0: " + render.preRenderMemory);
        }
        this.outputFormat = AudioFileFormat.parse(render.format, render.outputFile);
        Map<InputFile, File> outputs = new LinkedHashMap<>();
        for (InputFile input : inputs) {
            SongFormat inputFormat = ConverterFormats.parse(render.inputFormat, input.file(), "input", ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST);
            File output = batch ? input.getOutputFile(render.outputFile, this.outputFormat.getExtension()) : render.outputFile;
            outputs.put(input, output);
            this.jobs.add(new Job(input, inputFormat, output));
        }
        IOUtils.validateOutputFiles(outputs, render.overwrite);
        this.audioFormat = AudioOptionParser.parseAudioFormat(render.sampleRate, render.channels);
        this.limiter = AudioOptionParser.parseLimiter(render.limiter);
        this.resampler = AudioOptionParser.parseResampler(render.resampler);
        this.preRenderMemory = Objects.requireNonNullElseGet(render.preRenderMemory, () -> render.multithreaded ? 0 : DEFAULT_PRE_RENDER_MEMORY);
        this.render = render;
        if (render.stream && this.outputFormat == AudioFileFormat.WAV) {
            throw new ParameterException("WAV needs the length of the song before writing and can't be streamed. Use PCM, FLAC, MP3 or Opus with --stream");
        }
        if (render.stream && this.limiter == NoteBlockRenderer.Limiter.GLOBAL) {
            throw new ParameterException("The global limiter needs the whole song and can't be used with --stream. Use the windowed limiter or none");
        }

        this.pcmEncoding = this.outputFormat == AudioFileFormat.WAV || this.outputFormat == AudioFileFormat.PCM ? AudioOptionParser.parsePcmEncoding(render.pcmEncoding) : null;
        this.flacEncoding = this.outputFormat == AudioFileFormat.FLAC ? AudioOptionParser.createFlacEncoding(render.flacBitsPerSample, render.flacCompressionLevel) : null;
        this.mp3Encoding = this.outputFormat == AudioFileFormat.MP3 ? AudioOptionParser.createMp3Encoding(render.bitrateMode, render.bitrate, render.mp3Quality) : null;
        this.opusEncoding = this.outputFormat == AudioFileFormat.OPUS ? AudioOptionParser.createOpusEncoding(render.bitrateMode, render.bitrate) : null;
    }

    public void execute() {
        BatchProcessor.run(this.jobs, this.render.batch.failFast, !this.render.quiet, job -> job.input().name(), this::render);
    }

    private void render(final Job job) {
        File input = job.input().file();
        Song song;
        try {
            String songName = IOUtils.isStd(input) ? null : IOUtils.getNameWithoutExtension(input);
            song = NoteBlockLib.readSong(IOUtils.openInputStream(input), job.inputFormat(), songName);
        } catch (Throwable t) {
            throw new ParameterException("Failed to read input file: " + t.getMessage(), t);
        }
        if (song.getNotes().getLengthInTicks() <= 0) {
            throw new ParameterException("The song does not contain any notes");
        }

        AudioMixerNoteBlockRenderer.Options options = new AudioMixerNoteBlockRenderer.Options()
                .withSoundLoadExceptionHandler(this::onSoundLoadFailure)
                .withResamplerSupplier(this.resampler)
                .withTimingJitter(this.render.timingJitter)
                .withMultiThreaded(this.render.multithreaded)
                .withMaxSimultaneousSoundCount(this.render.maxSounds);
        NoteBlockRenderer renderer = new NoteBlockRenderer(song, this.audioFormat, this.createSoundProvider(), options);
        try (ProgressBar progress = ProgressBar.open(this.render.quiet)) {
            if (this.preRenderMemory > 0) {
                progress.start("Pre-rendering");
                renderer.preRenderMostUsedNotes(progress::update, this.preRenderMemory * 1024L * 1024L);
                progress.finish();
            }
            if (this.render.stream) {
                progress.start("Rendering");
                this.stream(job.output(), renderer, progress);
                progress.finish();
            } else {
                progress.start("Rendering");
                AudioBuffer buffer = renderer.renderFully(progress::update, this.render.volume, this.limiter);
                progress.finish();

                progress.start("Encoding");
                this.write(job.output(), song, buffer, progress);
                progress.finish();
            }
        }
        if (!this.render.quiet) {
            IOUtils.log(job.output(), "Successfully rendered " + IOUtils.getName(input, "STDIN") + " to " + IOUtils.getName(job.output(), "STDOUT") + " in " + this.outputFormat + " format");
        }
    }

    private void write(final File output, final Song song, final AudioBuffer buffer, final ProgressBar progress) {
        this.writeOutput(output, song, buffer.sampleCount(), audioOutputStream -> {
            float[] samples = buffer.samples();
            int chunkSize = this.audioFormat.millisToSampleCount(1000F);
            for (int offset = 0; offset < samples.length; offset += chunkSize) {
                int length = Math.min(chunkSize, samples.length - offset);
                audioOutputStream.write(samples, offset, length);
                progress.update((float) (offset + length) / samples.length * 100F);
            }
        });
    }

    private void stream(final File output, final NoteBlockRenderer renderer, final ProgressBar progress) {
        // Same processing as NoteBlockRenderer.Limiter, but applied by the root mixer to every rendered buffer.
        // StreamingNoteBlockRenderer isn't used, because its mixer always clips, even without a limiter
        AudioMixer mixer = renderer.getRootMixer();
        if (this.limiter == NoteBlockRenderer.Limiter.WINDOWED) mixer.processors().add(new LimiterProcessor());
        mixer.processors().add(new GainProcessor(this.render.volume / 100F));
        if (this.limiter == NoteBlockRenderer.Limiter.WINDOWED) mixer.processors().add(new HardClipProcessor());
        this.writeOutput(output, renderer.getSong(), -1, audioOutputStream -> {
            while (!renderer.hasReachedEnd()) {
                audioOutputStream.write(renderer.tick());
                progress.update(renderer.getProgress());
            }
            // Let the last notes ring out
            while (!renderer.getMixer().isEmpty()) {
                audioOutputStream.write(mixer.renderMillis(100F));
            }
        });
    }

    private void writeOutput(final File output, final Song song, final long sampleCount, final AudioWriter writer) {
        try (OutputStream outputStream = this.openOutputStream(output);
             AudioOutputStream audioOutputStream = this.createAudioOutputStream(outputStream, song, sampleCount)) {
            writer.write(audioOutputStream);
        } catch (Throwable t) {
            if (!IOUtils.isStd(output)) output.delete(); // Don't leave a broken file behind
            throw new ParameterException("Failed to write output file: " + t.getMessage(), t);
        }
    }

    private OutputStream openOutputStream(final File output) throws IOException {
        if (IOUtils.isStd(output)) {
            return new BufferedOutputStream(IOUtils.openOutputStream(output));
        }
        // Seeking allows FLAC and MP3 to write the song length and seek information after encoding
        IOUtils.createParentFolders(output);
        return new SeekableBufferedOutputStream(new SeekableFileOutputStream(output));
    }

    private AudioOutputStream createAudioOutputStream(final OutputStream outputStream, final Song song, final long sampleCount) throws IOException {
        return switch (this.outputFormat) {
            case WAV -> new WavPcmAudioOutputStream(outputStream, this.audioFormat, this.pcmEncoding, sampleCount);
            case PCM -> new PcmAudioOutputStream(outputStream, this.audioFormat, this.pcmEncoding);
            case FLAC -> new FlacAudioOutputStream(outputStream, this.audioFormat, this.flacEncoding, this.getVorbisTags(song));
            case MP3 -> new Mp3AudioOutputStream(outputStream, this.audioFormat, this.mp3Encoding, this.getMp3Tags(song));
            case OPUS -> OggOpusAudioOutputStream.createCompatible(outputStream, this.audioFormat, this.opusEncoding, OpusSignal.OPUS_SIGNAL_MUSIC, this.getVorbisTags(song));
        };
    }

    private void onSoundLoadFailure(final Instrument instrument, final Throwable t) {
        String name;
        if (instrument instanceof NbsCustomInstrument customInstrument) {
            name = "custom instrument '" + customInstrument.getNameOr("<no name>") + "' (" + customInstrument.getSoundFilePathOr("<no sound file>") + ")";
        } else {
            name = "instrument '" + instrument + "'";
        }
        System.err.println("Failed to load the sound for " + name + ", its notes will not be played. Use --sounds-dir to specify the folder containing the sound file");
    }

    private SoundProvider createSoundProvider() {
        SoundProvider soundProvider = new ResourceSoundProvider().then(new UrlSoundProvider());
        try {
            soundProvider = new NoteBlockStudioSoundProvider().then(soundProvider);
        } catch (Throwable ignored) {
        }
        if (this.render.soundsDir != null) {
            soundProvider = new NoteBlockStudioSoundProvider(this.render.soundsDir).then(soundProvider);
        }
        return soundProvider;
    }

    private Map<String, List<String>> getVorbisTags(final Song song) {
        Map<String, List<String>> tags = MetadataUtil.getVorbisTags(song);
        tags.put("Application", List.of("nbs-cli"));
        return tags;
    }

    private Mp3AudioOutputStream.Id3Tags getMp3Tags(final Song song) {
        Mp3AudioOutputStream.Id3Tags tags = MetadataUtil.getMp3Tags(song);
        tags = tags.withCustomField("Application", "nbs-cli");
        return tags;
    }


    @FunctionalInterface
    private interface AudioWriter {
        void write(AudioOutputStream audioOutputStream) throws IOException;
    }

    private record Job(InputFile input, SongFormat inputFormat, File output) {
    }

}
