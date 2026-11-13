package net.lenni0451.nbscli.commands.play;

import net.raphimc.audiomixer.util.buffer.AudioBuffer;
import net.raphimc.noteblocklib.model.song.Song;
import net.raphimc.noteblockrenderer.NoteBlockPlayer;
import net.raphimc.noteblockrenderer.provider.SoundProvider;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

class SongPlayer extends NoteBlockPlayer {

    // No field initializer: the writer thread already calls tick() inside the super constructor, before field initializers run
    private volatile boolean ready;

    SongPlayer(final SourceDataLine sourceDataLine, final Song song, final SoundProvider soundProvider, final Options options, final int startTick) throws LineUnavailableException {
        super(sourceDataLine, song, soundProvider, options);
        this.setTick(startTick);
        this.ready = true;
    }

    @Override
    public synchronized AudioBuffer tick() {
        if (!this.ready || this.isPaused()) {
            // Feed silence instead of an empty buffer, otherwise the writer thread busy-waits
            return new AudioBuffer(this.getRootMixer().getFormat(), this.getRootMixer().getFormat().millisToFrameCount(10));
        }
        return super.tick();
    }

    synchronized void seek(final int offsetMillis) {
        int target = this.getSong().millisecondsToTick(Math.max(0, this.getMillisecondPosition() + offsetMillis));
        this.setTick(Math.min(target, this.getSong().getNotes().getLengthInTicks()));
        this.getMixer().clear(); // Stop the sounds that are still ringing from the old position
        this.getSourceDataLineWriter().getSourceDataLine().flush();
    }

    synchronized boolean isMixerEmpty() {
        return this.getMixer().isEmpty();
    }

}
