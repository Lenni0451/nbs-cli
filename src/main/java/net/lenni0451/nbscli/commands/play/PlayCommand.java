package net.lenni0451.nbscli.commands.play;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.utils.AudioOptionParser;
import net.lenni0451.nbscli.utils.IOUtils;
import net.lenni0451.nbscli.utils.ResourceSoundProvider;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.nbs.model.NbsCustomInstrument;
import net.raphimc.noteblocklib.model.note.Instrument;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblockrenderer.AudioMixerNoteBlockRenderer;
import net.raphimc.noteblockrenderer.provider.SoundProvider;
import net.raphimc.noteblockrenderer.provider.impl.NoteBlockStudioSoundProvider;
import net.raphimc.noteblockrenderer.provider.impl.UrlSoundProvider;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;

public class PlayCommand {

    private static final int STATUS_INTERVAL_MILLIS = 100;
    private static final int TAIL_TIMEOUT_MILLIS = 10_000;
    private static final int LINE_BUFFER_PLAYOUT_MILLIS = 150;

    private final File file;
    private final SongFormat format;
    private final CLI.Play play;
    private final StartPosition start;
    private final AudioFormat audioFormat;

    private volatile boolean quit;

    public PlayCommand(final CLI.Play play) {
        if (!IOUtils.isStd(play.inputFile) && !play.inputFile.isFile()) {
            throw new ParameterException("Input file does not exist or is not a file: " + play.inputFile.getAbsolutePath());
        }
        if (play.soundsDir != null && !play.soundsDir.isDirectory()) {
            throw new ParameterException("Sounds folder does not exist or is not a folder: " + play.soundsDir.getAbsolutePath());
        }
        if (play.volume < 0 || play.volume > 100) {
            throw new ParameterException("Volume must be between 0 and 100: " + play.volume);
        }
        if (play.maxSounds < 1) {
            throw new ParameterException("Max sounds must be at least 1: " + play.maxSounds);
        }
        this.file = play.inputFile;
        this.format = ConverterFormats.parse(play.inputFormat, this.file, "input", ConverterFormats.INPUT_FORMATS_MAP, ConverterFormats.INPUT_FORMATS_LIST);
        this.play = play;
        this.start = play.start == null ? null : StartPosition.parse(play.start);
        var format = AudioOptionParser.parseAudioFormat(play.sampleRate, play.channels); // The mixer format, converted to a 16-bit Java Sound format below
        this.audioFormat = new AudioFormat(format.sampleRate(), 16, format.channelCount(), true, false);
    }

    public void execute() {
        Song song;
        try {
            String songName = IOUtils.isStd(this.file) ? null : IOUtils.getNameWithoutExtension(this.file);
            song = NoteBlockLib.readSong(IOUtils.openInputStream(this.file), this.format, songName);
        } catch (Throwable t) {
            throw new ParameterException("Failed to read input file: " + t.getMessage(), t);
        }
        int lengthInTicks = song.getNotes().getLengthInTicks();
        if (lengthInTicks <= 0) {
            throw new ParameterException("The song does not contain any notes");
        }
        int startTick = 0;
        if (this.start != null) {
            startTick = this.start.toTick(song);
            if (startTick < 0 || startTick >= lengthInTicks) {
                throw new ParameterException("Start position " + this.play.start + " is outside of the song (length: " + TimeFormat.format(song.getLengthInMilliseconds(), song.getLengthInMilliseconds()) + ", " + lengthInTicks + " ticks)");
            }
        }

        AudioMixerNoteBlockRenderer.Options options = new AudioMixerNoteBlockRenderer.Options()
                .withSoundLoadExceptionHandler(this::onSoundLoadFailure)
                .withTimingJitter(this.play.timingJitter)
                .withMultiThreaded(this.play.multithreaded)
                .withMaxSimultaneousSoundCount(this.play.maxSounds);

        try (SongPlayer player = new SongPlayer(this.openLine(), song, this.createSoundProvider(), options, startTick);
             PlayerView view = this.openView(player)) {
            player.getRootMixer().gain().set(this.play.volume / 100F);

            while (!this.quit && !player.hasReachedEnd()) {
                if (view != null) view.render();
                Thread.sleep(STATUS_INTERVAL_MILLIS);
            }
            if (this.quit) return;

            // Let the last notes ring out before closing the audio line
            long tailEnd = System.currentTimeMillis() + TAIL_TIMEOUT_MILLIS;
            while (!this.quit && !player.isMixerEmpty() && System.currentTimeMillis() < tailEnd) {
                if (view != null) view.render();
                Thread.sleep(STATUS_INTERVAL_MILLIS);
            }
            // The writer keeps feeding silence, so draining the line would block. Wait for the line buffer to play out instead
            Thread.sleep(LINE_BUFFER_PLAYOUT_MILLIS);
        } catch (LineUnavailableException e) {
            throw this.unsupportedFormat(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ParameterException e) {
            throw e;
        } catch (Throwable t) {
            throw new ParameterException("Failed to play song: " + t.getMessage(), t);
        }
    }

    private SourceDataLine openLine() throws LineUnavailableException {
        try {
            return AudioSystem.getSourceDataLine(this.audioFormat);
        } catch (IllegalArgumentException e) {
            throw this.unsupportedFormat(e);
        }
    }

    private ParameterException unsupportedFormat(final Exception cause) {
        // The cause is printed by Main, so it isn't repeated in the message
        return new ParameterException("Failed to open the audio device with " + (int) this.audioFormat.getSampleRate() + " Hz and " + this.audioFormat.getChannels() + " channel(s)", cause);
    }

    private SoundProvider createSoundProvider() {
        SoundProvider soundProvider = new ResourceSoundProvider().then(new UrlSoundProvider());
        try {
            soundProvider = new NoteBlockStudioSoundProvider().then(soundProvider);
        } catch (Throwable ignored) {
        }
        if (this.play.soundsDir != null) {
            soundProvider = new NoteBlockStudioSoundProvider(this.play.soundsDir).then(soundProvider);
        }
        return soundProvider;
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

    private PlayerView openView(final SongPlayer player) throws IOException {
        if (this.play.quiet) return null;
        String title = player.getSong().getTitleOrFileNameOr(IOUtils.getName(this.file, "STDIN"));
        if (this.play.simple) return new SimpleView(player, title);
        Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build();
        if (Terminal.TYPE_DUMB.equals(terminal.getType()) || Terminal.TYPE_DUMB_COLOR.equals(terminal.getType())) {
            // Not an interactive terminal (e.g. output is redirected), fall back to the simple progress output
            terminal.close();
            return new SimpleView(player, title);
        }
        return new TuiView(terminal, player, title, this.play.volume, () -> this.quit = true);
    }

}
