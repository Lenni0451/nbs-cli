package net.lenni0451.nbscli.model;

import net.raphimc.noteblocklib.format.SongFormat;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConverterFormatsTest {

    @Test
    void checkInputFormatsList() {
        String expected = Stream.of(SongFormat.values()).map(SongFormat::name).collect(Collectors.joining(", "));
        assertEquals(expected, ConverterFormats.INPUT_FORMATS_LIST);
    }

    @Test
    void checkInputFormatsMap() {
        Map<String, SongFormat> expected = new HashMap<>();
        for (SongFormat value : SongFormat.values()) {
            for (String extension : value.getExtensions()) {
                expected.put(extension.toLowerCase(), value);
            }
        }
        assertEquals(expected, ConverterFormats.INPUT_FORMATS_MAP);
    }

}
