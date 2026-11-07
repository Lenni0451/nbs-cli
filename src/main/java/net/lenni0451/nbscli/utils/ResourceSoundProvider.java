package net.lenni0451.nbscli.utils;

import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;
import net.raphimc.noteblocklib.model.note.Instrument;
import net.raphimc.noteblockrenderer.provider.SoundProvider;

import java.io.IOException;
import java.io.InputStream;

public class ResourceSoundProvider implements SoundProvider {

    @Override
    public InputStream getSoundInputStream(final Instrument instrument) throws IOException {
        if (instrument instanceof MinecraftInstrument minecraftInstrument) {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(minecraftInstrument.mcSoundPath() + ".ogg");
            if (is == null) {
                throw new IOException("Sound file not found for instrument: " + minecraftInstrument.name());
            }
            return is;
        }
        throw new IOException("Instrument is not a MinecraftInstrument: " + instrument.getClass().getName());
    }

}
