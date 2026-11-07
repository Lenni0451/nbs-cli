package net.lenni0451.nbscli.utils;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.io.PrintWriter;

public class ProgressBar implements AutoCloseable {

    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final int REDRAW_INTERVAL_MILLIS = 50;
    private static final int LABEL_WIDTH = 14;
    private static final PrintWriter STDERR = new PrintWriter(System.err, true);

    private final boolean quiet;
    private final Terminal terminal; // Null if the output isn't an interactive terminal
    private String label;
    private float progress;
    private long startTime;
    private long lastDraw;

    public static ProgressBar open(final boolean quiet) {
        if (quiet) return new ProgressBar(true, null);
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).systemOutput(TerminalBuilder.SystemOutput.SysErr).dumb(true).build();
            if (!Terminal.TYPE_DUMB.equals(terminal.getType()) && !Terminal.TYPE_DUMB_COLOR.equals(terminal.getType())) {
                return new ProgressBar(false, terminal);
            }
            terminal.close();
        } catch (IOException ignored) {
        }
        return new ProgressBar(false, null);
    }

    private ProgressBar(final boolean quiet, final Terminal terminal) {
        this.quiet = quiet;
        this.terminal = terminal;
    }

    public void message(final String message) {
        if (this.quiet) return;
        this.writer().println(message);
        this.writer().flush();
    }

    public void start(final String label) {
        this.label = label;
        this.progress = 0;
        this.startTime = System.currentTimeMillis();
        if (this.quiet) return;
        if (this.terminal == null) {
            this.writer().print(label + "...");
            this.writer().flush();
        } else {
            this.draw();
        }
    }

    public void update(final float progress) {
        this.progress = Float.isFinite(progress) ? Math.max(0, Math.min(100, progress)) : 0;
        if (this.terminal != null && System.currentTimeMillis() - this.lastDraw >= REDRAW_INTERVAL_MILLIS) {
            this.draw();
        }
    }

    public void finish() {
        if (this.label == null) return;
        this.progress = 100;
        if (!this.quiet) {
            if (this.terminal == null) {
                this.writer().println(" done in " + formatTime(System.currentTimeMillis() - this.startTime));
            } else {
                this.draw();
                this.writer().println();
            }
            this.writer().flush();
        }
        this.label = null;
    }

    @Override
    public void close() {
        if (this.label != null && !this.quiet) {
            // Interrupted by an error, move it to its own line
            this.writer().println();
            this.writer().flush();
        }
        this.label = null;
        if (this.terminal != null) {
            try {
                this.terminal.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void draw() {
        long now = System.currentTimeMillis();
        this.lastDraw = now;
        long elapsed = now - this.startTime;
        boolean done = this.progress >= 100;
        // Writing into the last column makes some terminals wrap the line
        int columns = this.terminal.getSize().getColumns();
        int width = (columns > 0 ? columns : 80) - 1;

        String time = done ? formatTime(elapsed) : this.progress >= 1 ? "ETA " + formatTime((long) (elapsed * (100 - this.progress) / this.progress)) : "ETA -:--";
        String prefix = " " + (done ? "✔" : SPINNER[(int) (elapsed / 100 % SPINNER.length)]) + " " + String.format("%-" + LABEL_WIDTH + "s", this.label);
        String suffix = String.format(" %3d%%  %-10s", (int) this.progress, time);
        int barWidth = Math.max(0, width - prefix.length() - suffix.length());
        int filled = (int) (barWidth * this.progress / 100);

        String line = new AttributedStringBuilder()
                .append(prefix, done ? AttributedStyle.BOLD.foreground(AttributedStyle.GREEN) : AttributedStyle.BOLD)
                .append("━".repeat(filled), AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                .append("─".repeat(barWidth - filled), AttributedStyle.DEFAULT.faint())
                .append(suffix)
                .toAttributedString().columnSubSequence(0, width).toAnsi(this.terminal);
        this.writer().print("\r" + line);
        this.terminal.puts(InfoCmp.Capability.clr_eol);
        this.terminal.flush();
    }

    private PrintWriter writer() {
        return this.terminal == null ? STDERR : this.terminal.writer();
    }

    private static String formatTime(final long millis) {
        long seconds = millis / 1000;
        if (seconds >= 3600) return String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

}
