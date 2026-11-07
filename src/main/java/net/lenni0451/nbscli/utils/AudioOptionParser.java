package net.lenni0451.nbscli.utils;

import com.beust.jcommander.ParameterException;
import net.raphimc.audiomixer.io.flac.FlacAudioOutputStream;
import net.raphimc.audiomixer.io.mp3.Mp3AudioOutputStream;
import net.raphimc.audiomixer.io.ogg.opus.OggOpusAudioOutputStream;
import net.raphimc.audiomixer.resampler.Resampler;
import net.raphimc.audiomixer.resampler.impl.BlackmanSincResampler;
import net.raphimc.audiomixer.resampler.impl.CubicResampler;
import net.raphimc.audiomixer.resampler.impl.LinearResampler;
import net.raphimc.audiomixer.resampler.impl.PointResampler;
import net.raphimc.audiomixer.util.AudioFormat;
import net.raphimc.audiomixer.util.PcmSampleEncoding;
import net.raphimc.noteblockrenderer.NoteBlockRenderer;

import java.util.Locale;
import java.util.function.Supplier;

public final class AudioOptionParser {

    private static final int DEFAULT_MP3_BITRATE = 224;
    private static final int DEFAULT_OPUS_BITRATE = 128;

    public static AudioFormat parseAudioFormat(final int sampleRate, final String channels) {
        if (sampleRate < 1) {
            throw new ParameterException("Sample rate must be at least 1 Hz: " + sampleRate);
        }
        return new AudioFormat(sampleRate, parseChannels(channels));
    }

    public static int parseChannels(final String channels) {
        return switch (channels.toLowerCase(Locale.ROOT)) {
            case "mono" -> 1;
            case "stereo" -> 2;
            default -> throw new ParameterException("Invalid channel layout: " + channels + ". Supported values are: mono, stereo");
        };
    }

    public static NoteBlockRenderer.Limiter parseLimiter(final String limiter) {
        try {
            return NoteBlockRenderer.Limiter.valueOf(limiter.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParameterException("Invalid limiter: " + limiter + ". Supported values are: windowed, global, none");
        }
    }

    public static Supplier<Resampler> parseResampler(final String resampler) {
        return switch (resampler.toLowerCase(Locale.ROOT)) {
            case "point" -> PointResampler::new;
            case "linear" -> LinearResampler::new;
            case "cubic" -> CubicResampler::new;
            case "sinc" -> BlackmanSincResampler::new;
            default -> throw new ParameterException("Invalid resampler: " + resampler + ". Supported values are: point, linear, cubic, sinc");
        };
    }

    public static PcmSampleEncoding parsePcmEncoding(final String encoding) {
        return switch (encoding.toLowerCase(Locale.ROOT)) {
            case "u8" -> PcmSampleEncoding.U8;
            case "s16" -> PcmSampleEncoding.S16_LE;
            case "s24" -> PcmSampleEncoding.S24_LE;
            case "s32" -> PcmSampleEncoding.S32_LE;
            case "f32" -> PcmSampleEncoding.F32_LE;
            default -> throw new ParameterException("Invalid PCM encoding: " + encoding + ". Supported values are: u8, s16, s24, s32, f32");
        };
    }

    public static FlacAudioOutputStream.Encoding createFlacEncoding(final int bitsPerSample, final int compressionLevel) {
        try {
            return new FlacAudioOutputStream.Encoding(bitsPerSample, compressionLevel);
        } catch (IllegalArgumentException e) {
            throw new ParameterException("Invalid FLAC encoding: " + e.getMessage());
        }
    }

    public static Mp3AudioOutputStream.Encoding createMp3Encoding(final String bitrateMode, final Integer bitrate, final int quality) {
        String mode = parseBitrateMode(bitrateMode);
        if (mode.equals("vbr") && bitrate != null) {
            throw new ParameterException("--bitrate is only used with --bitrate-mode cbr or abr for MP3. Use --mp3-quality for vbr");
        }
        if (quality < 0 || quality > 100) {
            throw new ParameterException("MP3 quality must be between 0 and 100: " + quality);
        }
        int kbps = bitrate == null ? DEFAULT_MP3_BITRATE : bitrate;
        try {
            return switch (mode) {
                case "cbr" -> Mp3AudioOutputStream.Encoding.cbr(kbps);
                case "abr" -> Mp3AudioOutputStream.Encoding.abr(kbps);
                default -> Mp3AudioOutputStream.Encoding.vbr(quality / 100F);
            };
        } catch (IllegalArgumentException e) {
            throw new ParameterException("Invalid MP3 bitrate: " + e.getMessage());
        }
    }

    public static OggOpusAudioOutputStream.Encoding createOpusEncoding(final String bitrateMode, final Integer bitrate) {
        String mode = parseBitrateMode(bitrateMode);
        int kbps = bitrate == null ? DEFAULT_OPUS_BITRATE : bitrate;
        try {
            return switch (mode) {
                case "cbr" -> OggOpusAudioOutputStream.Encoding.cbr(kbps);
                case "abr" -> OggOpusAudioOutputStream.Encoding.abr(kbps);
                default -> OggOpusAudioOutputStream.Encoding.vbr(kbps);
            };
        } catch (IllegalArgumentException e) {
            throw new ParameterException("Invalid Opus bitrate: " + e.getMessage());
        }
    }

    private static String parseBitrateMode(final String mode) {
        String lower = mode.toLowerCase(Locale.ROOT);
        if (!lower.equals("cbr") && !lower.equals("abr") && !lower.equals("vbr")) {
            throw new ParameterException("Invalid bitrate mode: " + mode + ". Supported values are: cbr, abr, vbr");
        }
        return lower;
    }

}
