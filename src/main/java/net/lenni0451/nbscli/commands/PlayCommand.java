package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.model.ConverterFormats;
import net.lenni0451.nbscli.utils.IOUtils;
import net.lenni0451.nbscli.utils.ResourceSoundProvider;
import net.raphimc.audiomixer.util.buffer.AudioBuffer;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.nbs.model.NbsCustomInstrument;
import net.raphimc.noteblocklib.model.note.Instrument;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblockrenderer.AudioMixerNoteBlockRenderer;
import net.raphimc.noteblockrenderer.NoteBlockPlayer;
import net.raphimc.noteblockrenderer.provider.SoundProvider;
import net.raphimc.noteblockrenderer.provider.impl.NoteBlockStudioSoundProvider;
import net.raphimc.noteblockrenderer.provider.impl.UrlSoundProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.File;

public class PlayCommand {

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(48000, 16, 2, true, false);
    private static final int STATUS_INTERVAL_MILLIS = 100;
    private static final int TAIL_TIMEOUT_MILLIS = 10_000;
    private static final int LINE_BUFFER_PLAYOUT_MILLIS = 150;

    private final File file;
    private final SongFormat format;
    private final CLI.Play play;
    private final Position start;

    private int startTick;
    private boolean startApplied;
    private String lastStatus = "";

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
        this.start = play.start == null ? null : Position.parse(play.start);
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
        if (this.start != null) {
            this.startTick = this.start.tick ? this.start.value : song.millisecondsToTick(this.start.value);
            if (this.startTick < 0 || this.startTick >= lengthInTicks) {
                throw new ParameterException("Start position " + this.play.start + " is outside of the song (length: " + formatTime(song.getLengthInMilliseconds(), song.getLengthInMilliseconds()) + ", " + lengthInTicks + " ticks)");
            }
        }

        AudioMixerNoteBlockRenderer.Options options = new AudioMixerNoteBlockRenderer.Options()
                .withSoundLoadExceptionHandler(PlayCommand::onSoundLoadFailure)
                .withTimingJitter(this.play.timingJitter)
                .withMultiThreaded(this.play.multithreaded)
                .withMaxSimultaneousSoundCount(this.play.maxSounds);

        try (Player player = new Player(AudioSystem.getSourceDataLine(AUDIO_FORMAT), song, this.createSoundProvider(), options)) {
            player.getRootMixer().gain().set(this.play.volume / 100F);
            if (!this.play.quiet) this.printInfo(song);

            while (!player.hasReachedEnd()) {
                this.printStatus(player);
                Thread.sleep(STATUS_INTERVAL_MILLIS);
            }

            // Let the last notes ring out before closing the audio line
            long tailEnd = System.currentTimeMillis() + TAIL_TIMEOUT_MILLIS;
            while (!player.isMixerEmpty() && System.currentTimeMillis() < tailEnd) {
                Thread.sleep(STATUS_INTERVAL_MILLIS);
            }
            // The writer keeps feeding silence, so draining the line would block. Wait for the line buffer to play out instead
            Thread.sleep(LINE_BUFFER_PLAYOUT_MILLIS);
            this.printStatus(player);
            if (!this.play.quiet) System.out.println();
        } catch (LineUnavailableException e) {
            throw new ParameterException("Failed to open audio line: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ParameterException e) {
            throw e;
        } catch (Throwable t) {
            throw new ParameterException("Failed to play song: " + t.getMessage(), t);
        }
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

    private static void onSoundLoadFailure(final Instrument instrument, final Throwable t) {
        String name;
        if (instrument instanceof NbsCustomInstrument customInstrument) {
            name = "custom instrument '" + customInstrument.getNameOr("<no name>") + "' (" + customInstrument.getSoundFilePathOr("<no sound file>") + ")";
        } else {
            name = "instrument '" + instrument + "'";
        }
        System.err.println("Failed to load the sound for " + name + ", its notes will not be played. Use --sounds-dir to specify the folder containing the sound file");
    }

    private void printInfo(final Song song) {
        System.out.println("Playing " + song.getTitleOrFileNameOr(IOUtils.getName(this.file, "STDIN")));
        if (song.getAuthor() != null && !song.getAuthor().isBlank()) {
            System.out.println("  Author: " + song.getAuthor());
        }
        if (song.getOriginalAuthor() != null && !song.getOriginalAuthor().isBlank()) {
            System.out.println("  Original Author: " + song.getOriginalAuthor());
        }
        System.out.println("  Length: " + formatTime(song.getLengthInMilliseconds(), song.getLengthInMilliseconds()));
        System.out.println("  Notes: " + "%,d".formatted(song.getNotes().getNoteCount()));
    }

    private void printStatus(final Player player) {
        if (this.play.quiet) return;
        Song song = player.getSong();
        int length = song.getLengthInMilliseconds();
        int position = Math.min(player.getMillisecondPosition(), length);
        String status = "Playing " + formatTime(position, length) + " / " + formatTime(length, length);
        if (status.equals(this.lastStatus)) return;
        System.out.print("\r" + status + " ".repeat(Math.max(0, this.lastStatus.length() - status.length())));
        System.out.flush();
        this.lastStatus = status;
    }

    private static String formatTime(final int millis, final int lengthMillis) {
        int seconds = Math.max(0, millis) / 1000;
        if (lengthMillis >= 3_600_000) {
            return String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d", seconds / 60, seconds % 60);
        }
    }


    private final class Player extends NoteBlockPlayer {
        private Player(final SourceDataLine sourceDataLine, final Song song, final SoundProvider soundProvider, final Options options) throws LineUnavailableException {
            super(sourceDataLine, song, soundProvider, options);
        }

        @Override
        public synchronized AudioBuffer tick() {
            if (!PlayCommand.this.startApplied) {
                PlayCommand.this.startApplied = true;
                this.setTick(PlayCommand.this.startTick);
            }
            return super.tick();
        }

        private synchronized boolean isMixerEmpty() {
            return this.getMixer().isEmpty();
        }
    }

    private record Position(int value, boolean tick) {
        private static Position parse(final String input) {
            String value = input.trim();
            try {
                if (value.chars().allMatch(Character::isDigit) && !value.isEmpty()) {
                    return new Position(Integer.parseInt(value), true);
                }
                String[] parts = value.split(":", -1);
                if (parts.length == 2 || parts.length == 3) {
                    int hours = parts.length == 3 ? parseTimePart(parts[0], Integer.MAX_VALUE) : 0;
                    int minutes = parseTimePart(parts[parts.length - 2], parts.length == 3 ? 59 : Integer.MAX_VALUE);
                    int seconds = parseTimePart(parts[parts.length - 1], 59);
                    long millis = ((long) hours * 3600 + (long) minutes * 60 + seconds) * 1000;
                    if (millis <= Integer.MAX_VALUE) {
                        return new Position((int) millis, false);
                    }
                }
            } catch (Throwable ignored) {
            }
            throw new ParameterException("Invalid start position: " + input + ". Expected [hh:]mm:ss or a tick number");
        }

        private static int parseTimePart(final String part, final int max) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) throw new NumberFormatException(part);
            int value = Integer.parseInt(part);
            if (value > max) throw new NumberFormatException(part);
            return value;
        }
    }

}
