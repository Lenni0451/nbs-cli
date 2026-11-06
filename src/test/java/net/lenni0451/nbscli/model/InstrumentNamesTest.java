package net.lenni0451.nbscli.model;

import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstrumentNamesTest {

    @Test
    void checkInstrumentNames() {
        String expected = Stream.of(MinecraftInstrument.values()).map(MinecraftInstrument::name).map(name -> name.toLowerCase().replace('_', '-')).collect(Collectors.joining(", "));
        assertEquals(expected, InstrumentNames.LIST);
    }

}
