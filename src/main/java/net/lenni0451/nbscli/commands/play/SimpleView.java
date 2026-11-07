package net.lenni0451.nbscli.commands.play;

import net.raphimc.noteblocklib.model.song.Song;

class SimpleView implements PlayerView {

    private final SongPlayer player;
    private String lastStatus = "";

    SimpleView(final SongPlayer player, final String title) {
        this.player = player;
        Song song = player.getSong();
        System.out.println("Playing " + title);
        if (song.getAuthor() != null && !song.getAuthor().isBlank()) {
            System.out.println("  Author: " + song.getAuthor());
        }
        if (song.getOriginalAuthor() != null && !song.getOriginalAuthor().isBlank()) {
            System.out.println("  Original Author: " + song.getOriginalAuthor());
        }
        System.out.println("  Length: " + TimeFormat.format(song.getLengthInMilliseconds(), song.getLengthInMilliseconds()));
        System.out.println("  Notes: " + "%,d".formatted(song.getNotes().getNoteCount()));
    }

    @Override
    public void render() {
        int length = this.player.getSong().getLengthInMilliseconds();
        int position = Math.min(this.player.getMillisecondPosition(), length);
        String status = "Playing " + TimeFormat.format(position, length) + " / " + TimeFormat.format(length, length);
        if (status.equals(this.lastStatus)) return;
        // Pad with spaces to overwrite leftovers of a longer previous status
        System.out.print("\r" + status + " ".repeat(Math.max(0, this.lastStatus.length() - status.length())));
        System.out.flush();
        this.lastStatus = status;
    }

    @Override
    public void close() {
        this.render();
        System.out.println();
    }

}
