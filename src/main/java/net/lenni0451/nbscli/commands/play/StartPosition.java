package net.lenni0451.nbscli.commands.play;

import com.beust.jcommander.ParameterException;
import net.raphimc.noteblocklib.model.song.Song;

record StartPosition(int value, boolean tick) {

    static StartPosition parse(final String input) {
        String value = input.trim();
        try {
            if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) {
                return new StartPosition(Integer.parseInt(value), true);
            }
            String[] parts = value.split(":", -1);
            if (parts.length == 2 || parts.length == 3) {
                int hours = parts.length == 3 ? parsePart(parts[0], Integer.MAX_VALUE) : 0;
                int minutes = parsePart(parts[parts.length - 2], parts.length == 3 ? 59 : Integer.MAX_VALUE);
                int seconds = parsePart(parts[parts.length - 1], 59);
                long millis = ((long) hours * 3600 + (long) minutes * 60 + seconds) * 1000;
                if (millis <= Integer.MAX_VALUE) {
                    return new StartPosition((int) millis, false);
                }
            }
        } catch (Throwable ignored) {
        }
        throw new ParameterException("Invalid start position: " + input + ". Expected [hh:]mm:ss or a tick number");
    }

    private static int parsePart(final String part, final int max) {
        if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) throw new NumberFormatException(part);
        int value = Integer.parseInt(part);
        if (value > max) throw new NumberFormatException(part);
        return value;
    }

    int toTick(final Song song) {
        return this.tick ? this.value : song.millisecondsToTick(this.value);
    }

}
