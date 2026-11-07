package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.AudioFileFormat;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.utils.AudioOptionParser;
import net.lenni0451.nbscli.utils.IOUtils;
import net.raphimc.audiomixer.io.flac.FlacAudioOutputStream;
import net.raphimc.audiomixer.io.mp3.Mp3AudioOutputStream;
import net.raphimc.audiomixer.io.ogg.opus.OggOpusAudioOutputStream;
import net.raphimc.audiomixer.util.AudioFormat;
import net.raphimc.audiomixer.util.PcmSampleEncoding;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblockrenderer.NoteBlockRenderer;

import java.io.File;

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

    }

}
