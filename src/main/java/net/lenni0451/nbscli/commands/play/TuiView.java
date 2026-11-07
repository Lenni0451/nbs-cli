package net.lenni0451.nbscli.commands.play;

import net.raphimc.noteblocklib.model.song.Song;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.*;

import java.io.IOException;
import java.util.List;

class TuiView implements PlayerView {

    private static final int SEEK_MILLIS = 5000;
    private static final int VOLUME_STEP = 5;

    private final Terminal terminal;
    private final SongPlayer player;
    private final Runnable onQuit;
    private final Attributes originalAttributes;
    private final Display display;
    private final String title;
    private final String author;
    private volatile int volume;

    TuiView(final Terminal terminal, final SongPlayer player, final String title, final int volume, final Runnable onQuit) {
        this.terminal = terminal;
        this.player = player;
        this.onQuit = onQuit;
        this.originalAttributes = terminal.enterRawMode();
        this.display = new Display(terminal, false);
        Song song = player.getSong();
        this.title = title;
        this.author = song.getAuthor() == null || song.getAuthor().isBlank() ? null : song.getAuthor();
        this.volume = volume;

        terminal.handle(Terminal.Signal.INT, signal -> onQuit.run());
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        Thread input = new Thread(this::readKeys, "TUI input");
        input.setDaemon(true);
        input.start();
    }

    private void readKeys() {
        KeyMap<Runnable> keys = new KeyMap<>();
        keys.setNomatch(() -> {
        });
        keys.bind(() -> this.player.setPaused(!this.player.isPaused()), " ");
        keys.bind(this.onQuit, "q", "Q");
        this.bind(keys, () -> this.player.seek(-SEEK_MILLIS), InfoCmp.Capability.key_left, "\033[D", "\033OD");
        this.bind(keys, () -> this.player.seek(SEEK_MILLIS), InfoCmp.Capability.key_right, "\033[C", "\033OC");
        this.bind(keys, () -> this.changeVolume(VOLUME_STEP), InfoCmp.Capability.key_up, "\033[A", "\033OA");
        this.bind(keys, () -> this.changeVolume(-VOLUME_STEP), InfoCmp.Capability.key_down, "\033[B", "\033OB");

        BindingReader reader = new BindingReader(this.terminal.reader());
        try {
            Runnable action;
            while ((action = reader.readBinding(keys)) != null) {
                action.run();
            }
        } catch (EndOfFileException ignored) {
            // The terminal was closed
        }
    }

    private void bind(final KeyMap<Runnable> keys, final Runnable action, final InfoCmp.Capability capability, final String... fallbacks) {
        String sequence = KeyMap.key(this.terminal, capability);
        if (sequence != null) keys.bind(action, sequence);
        keys.bind(action, fallbacks);
    }

    private void changeVolume(final int delta) {
        this.volume = Math.max(0, Math.min(100, this.volume + delta));
        this.player.getRootMixer().gain().set(this.volume / 100F);
    }

    @Override
    public void render() {
        Size size = this.terminal.getSize();
        // Some terminals don't report their size
        if (size.getColumns() <= 0 || size.getRows() <= 0) size = Size.of(size.getColumns() > 0 ? size.getColumns() : 80, size.getRows() > 0 ? size.getRows() : 24);
        int width = size.getColumns();
        int length = this.player.getSong().getLengthInMilliseconds();
        int position = Math.min(this.player.getMillisecondPosition(), length);
        String state = this.player.hasReachedEnd() ? "■ Finished" : this.player.isPaused() ? "❚❚ Paused " : "▶ Playing";
        String time = TimeFormat.format(position, length) + " / " + TimeFormat.format(length, length);
        int barWidth = Math.max(0, width - state.length() - time.length() - 6);
        int filled = length <= 0 ? 0 : (int) ((long) barWidth * position / length);
        AttributedStyle faint = AttributedStyle.DEFAULT.faint();

        List<AttributedString> lines = List.of(
                new AttributedStringBuilder().append(" ♪ ").append(this.title, AttributedStyle.BOLD)
                        .append(this.author == null ? "" : " by " + this.author, faint).toAttributedString(),
                new AttributedStringBuilder().append(" " + state + " ")
                        .append("━".repeat(filled), AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                        .append("─".repeat(barWidth - filled), faint)
                        .append(" " + time).toAttributedString(),
                new AttributedString(" Sounds %,d · CPU %.0f%% · Volume %d%%".formatted(this.player.getMixer().getMixedSourceCount(), this.player.getSourceDataLineWriter().getProcessingLoad(), this.volume), faint),
                new AttributedString(" space pause/resume · ←/→ seek " + (SEEK_MILLIS / 1000) + "s · ↑/↓ volume · q quit", faint)
        ).stream().map(line -> line.columnSubSequence(0, width)).toList();
        this.display.resize(size);
        this.display.update(lines, size.cursorPos(lines.size() - 1, lines.get(lines.size() - 1).columnLength()));
        this.terminal.flush();
    }

    @Override
    public void close() {
        this.render();
        this.terminal.puts(InfoCmp.Capability.cursor_visible);
        this.terminal.writer().println();
        this.terminal.setAttributes(this.originalAttributes);
        try {
            this.terminal.close();
        } catch (IOException ignored) {
        }
    }

}
