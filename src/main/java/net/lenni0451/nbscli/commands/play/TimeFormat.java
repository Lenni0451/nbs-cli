package net.lenni0451.nbscli.commands.play;

final class TimeFormat {

    private TimeFormat() {
    }

    static String format(final int millis, final int lengthMillis) {
        int seconds = Math.max(0, millis) / 1000;
        if (lengthMillis >= 3_600_000) {
            return String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d", seconds / 60, seconds % 60);
        }
    }

}
