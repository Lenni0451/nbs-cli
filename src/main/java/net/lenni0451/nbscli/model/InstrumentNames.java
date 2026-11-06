package net.lenni0451.nbscli.model;

import com.beust.jcommander.ParameterException;
import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;

import java.util.Locale;

public class InstrumentNames {

    public static final String LIST = "harp, bass-drum, snare, hat, bass, flute, bell, guitar, chime, xylophone, iron-xylophone, cow-bell, didgeridoo, bit, banjo, pling, trumpet, trumpet-exposed, trumpet-oxidized, trumpet-weathered";

    public static MinecraftInstrument parse(final String name) {
        try {
            return MinecraftInstrument.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new ParameterException("Unknown instrument: " + name + ". Supported instruments are: " + LIST);
        }
    }

    public static String format(final MinecraftInstrument instrument) {
        return instrument.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

}
