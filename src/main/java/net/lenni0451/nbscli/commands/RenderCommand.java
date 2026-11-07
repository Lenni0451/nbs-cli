package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.AudioFileFormat;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.utils.AudioOptionParser;
import net.lenni0451.nbscli.utils.IOUtils;
import net.lenni0451.nbscli.utils.ProgressBar;
import net.lenni0451.nbscli.utils.ResourceSoundProvider;
import net.raphimc.audiomixer.io.AudioOutputStream;
import net.raphimc.audiomixer.io.flac.FlacAudioOutputStream;
import net.raphimc.audiomixer.io.mp3.Mp3AudioOutputStream;
import net.raphimc.audiomixer.io.ogg.opus.OggOpusAudioOutputStream;
import net.raphimc.audiomixer.io.pcm.PcmAudioOutputStream;
import net.raphimc.audiomixer.io.wav.pcm.WavPcmAudioOutputStream;
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
import java.util.List;
import java.util.Map;

public class RenderCommand {

    private final File input;
    private final File output;
    private final SongFormat inputFormat;
    private final AudioFileFormat outputFormat;
    private final AudioFormat audioFormat;
    private final NoteBlockRenderer.Limiter limiter;
    private final CLI.Render render;
    // Only the encoding of the selected output format is set, the others are null
    private final PcmSampleEncoding pcmEncoding;
    private final FlacAudioOutputStream.Encoding flacEncoding;
    private final Mp3AudioOutputStream.Encoding mp3Encoding;
    private final OggOpusAudioOutputStream.Encoding opusEncoding;

    public RenderCommand(final CLI.Render render) {
        if (!IOUtils.isStd(render.inputFile) && !render.inputFile.isFile()) {
            throw new ParameterException("Input file does not exist or is not a file: " + render.inputFile.getAbsolutePath());
        }
        if (!IOUtils.isStd(render.outputFile) && render.outputFile.exists() && !render.overwrite) {
            throw new ParameterException("Output file already exists and overwrite is not allowed: " + render.outputFile.getAbsolutePath());
        }
        if (!IOUtils.isStd(render.outputFile) && render.outputFile.isDirectory()) {
            throw new ParameterException("Output file is a directory: " + render.outputFile.getAbsolutePath());
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
        if (render.preRenderMemory < 0) {
            throw new ParameterException("Pre-render memory must be at least 0: " + render.preRenderMemory);
        }
        this.input = render.inputFile;
        this.output = render.outputFile;
        this.inputFormat = ConverterFormats.parse(render.inputFormat, this.input, "input", ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST);
        this.outputFormat = AudioFileFormat.parse(render.format, this.output);
        this.audioFormat = AudioOptionParser.parseAudioFormat(render.sampleRate, render.channels);
        this.limiter = AudioOptionParser.parseLimiter(render.limiter);
        this.render = render;

        this.pcmEncoding = this.outputFormat == AudioFileFormat.WAV || this.outputFormat == AudioFileFormat.PCM ? AudioOptionParser.parsePcmEncoding(render.pcmEncoding) : null;
        this.flacEncoding = this.outputFormat == AudioFileFormat.FLAC ? AudioOptionParser.createFlacEncoding(render.flacBitsPerSample, render.flacCompressionLevel) : null;
        this.mp3Encoding = this.outputFormat == AudioFileFormat.MP3 ? AudioOptionParser.createMp3Encoding(render.bitrateMode, render.bitrate, render.mp3Quality) : null;
        this.opusEncoding = this.outputFormat == AudioFileFormat.OPUS ? AudioOptionParser.createOpusEncoding(render.bitrateMode, render.bitrate) : null;
    }

    public void execute() {
        Song song;
        try {
            String songName = IOUtils.isStd(this.input) ? null : IOUtils.getNameWithoutExtension(this.input);
            song = NoteBlockLib.readSong(IOUtils.openInputStream(this.input), this.inputFormat, songName);
        } catch (Throwable t) {
            throw new ParameterException("Failed to read input file: " + t.getMessage(), t);
        }
        if (song.getNotes().getLengthInTicks() <= 0) {
            throw new ParameterException("The song does not contain any notes");
        }

        AudioMixerNoteBlockRenderer.Options options = new AudioMixerNoteBlockRenderer.Options()
                .withSoundLoadExceptionHandler(this::onSoundLoadFailure)
                .withTimingJitter(this.render.timingJitter)
                .withMultiThreaded(this.render.multithreaded)
                .withMaxSimultaneousSoundCount(this.render.maxSounds);
        NoteBlockRenderer renderer = new NoteBlockRenderer(song, this.audioFormat, this.createSoundProvider(), options);
        try (ProgressBar progress = ProgressBar.open(this.render.quiet)) {
            if (this.render.preRenderMemory > 0) {
                progress.start("Pre-rendering");
                renderer.preRenderMostUsedNotes(progress::update, this.render.preRenderMemory * 1024L * 1024L);
                progress.finish();
            }
            progress.start("Rendering");
            AudioBuffer buffer = renderer.renderFully(progress::update, this.render.volume, this.limiter);
            progress.finish();

            progress.start("Encoding");
            this.write(song, buffer, progress);
            progress.finish();
        }
        if (!this.render.quiet) {
            IOUtils.log(this.output, "Successfully rendered " + IOUtils.getName(this.input, "STDIN") + " to " + IOUtils.getName(this.output, "STDOUT") + " in " + this.outputFormat + " format");
        }
    }

    private void write(final Song song, final AudioBuffer buffer, final ProgressBar progress) {
        try (OutputStream outputStream = this.openOutputStream();
             AudioOutputStream audioOutputStream = this.createAudioOutputStream(outputStream, song, buffer)) {
            float[] samples = buffer.samples();
            int chunkSize = this.audioFormat.millisToSampleCount(1000F);
            for (int offset = 0; offset < samples.length; offset += chunkSize) {
                int length = Math.min(chunkSize, samples.length - offset);
                audioOutputStream.write(samples, offset, length);
                progress.update((float) (offset + length) / samples.length * 100F);
            }
        } catch (Throwable t) {
            if (!IOUtils.isStd(this.output)) this.output.delete(); // Don't leave a broken file behind
            throw new ParameterException("Failed to write output file: " + t.getMessage(), t);
        }
    }

    private OutputStream openOutputStream() throws IOException {
        if (IOUtils.isStd(this.output)) {
            return new BufferedOutputStream(IOUtils.openOutputStream(this.output));
        }
        // Seeking allows FLAC and MP3 to write the song length and seek information after encoding
        return new SeekableBufferedOutputStream(new SeekableFileOutputStream(this.output));
    }

    private AudioOutputStream createAudioOutputStream(final OutputStream outputStream, final Song song, final AudioBuffer buffer) throws IOException {
        return switch (this.outputFormat) {
            case WAV -> new WavPcmAudioOutputStream(outputStream, this.audioFormat, this.pcmEncoding, buffer.sampleCount());
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

}
