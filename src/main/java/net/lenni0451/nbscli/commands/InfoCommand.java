package net.lenni0451.nbscli.commands;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.CLI;
import net.lenni0451.nbscli.utils.IOUtils;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.minecraft.MinecraftInstrument;
import net.raphimc.noteblocklib.format.nbs.model.NbsCustomInstrument;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
import net.raphimc.noteblocklib.util.SongUtil;

import java.io.File;
import java.util.Set;

public class InfoCommand {

    private static final String SPACER = "  ";

    private final File file;
    private final boolean full;

    public InfoCommand(final CLI.Info info) {
        if (!IOUtils.isStd(info.file) && !info.file.isFile()) {
            throw new ParameterException("Input file does not exist or is not a file: " + info.file.getAbsolutePath());
        }
        this.file = info.file;
        this.full = info.full;
    }

    public void execute() {
        NbsSong song;
        try {
            song = (NbsSong) NoteBlockLib.readSong(IOUtils.openInputStream(this.file), SongFormat.NBS, IOUtils.isStd(this.file) ? null : IOUtils.getNameWithoutExtension(this.file));
        } catch (Throwable t) {
            throw new ParameterException("Failed to read NBS file: " + t.getMessage(), t);
        }

        System.out.println("Information about " + IOUtils.getName(this.file, "STDIN") + ":");
        this.print("Title", song.getTitle());
        this.print("Author", song.getAuthor());
        this.print("Original Author", song.getOriginalAuthor());
        this.print("Description", song.getDescription());
        this.print("Source File", song.getSourceFileName());
        this.print("Time spent", "%,d min".formatted(song.getMinutesSpent()));
        this.print("Length", song.getHumanReadableLength());
        this.print("Notes", "%,d".formatted(song.getNotes().getNoteCount()));
        this.print("Tempo", song.getTempoEvents().getHumanReadableTempoRange() + " t/s");

        Set<MinecraftInstrument> vanillaInstruments = SongUtil.getUsedVanillaInstruments(song);
        Set<NbsCustomInstrument> customInstruments = SongUtil.getUsedNbsCustomInstruments(song);
        if (!this.full) {
            this.print("Instruments", this.formatInstruments(vanillaInstruments.size(), customInstruments.size()));
        } else {
            System.out.println(SPACER + "Instruments:");
            if (!vanillaInstruments.isEmpty()) {
                System.out.println(SPACER.repeat(2) + "Vanilla (" + vanillaInstruments.size() + "):");
                for (MinecraftInstrument instrument : vanillaInstruments) {
                    this.print(3, instrument.name(), instrument.mcSoundName());
                }
            }
            if (!customInstruments.isEmpty()) {
                System.out.println(SPACER.repeat(2) + "Custom (" + customInstruments.size() + "):");
                for (NbsCustomInstrument instrument : customInstruments) {
                    this.print(3, instrument.getNameOr("<no name>"), instrument.getSoundFilePathOr("<no sound file>"));
                }
            }

            this.print("Version", String.valueOf(song.getVersion()));
            this.print("Left Clicks", "%,d".formatted(song.getLeftClicks()));
            this.print("Right Clicks", "%,d".formatted(song.getRightClicks()));
            this.print("Note Blocks Added", "%,d".formatted(song.getNoteBlocksAdded()));
            this.print("Note Blocks Removed", "%,d".formatted(song.getNoteBlocksRemoved()));
            this.print("Auto Save Interval", "%,d".formatted(song.getAutoSaveInterval()));
            this.print("Time Signature", String.valueOf(song.getTimeSignature()));
            this.print("Max Loop Count", "%,d".formatted(song.getMaxLoopCount()));
            this.print("Loop Start Tick", String.valueOf(song.getLoopStartTick()));
            this.print("Vanilla Instrument Count", "%,d".formatted(song.getVanillaInstrumentCount()));
            this.print("Layer Count", "%,d".formatted(song.getLayerCount()));
            this.print("NBS Tempo", "%,.2f".formatted(song.getTempo() / 100F));
        }
    }

    private void print(final String title, final String value) {
        this.print(1, title, value);
    }

    private void print(final int layer, final String title, final String value) {
        if (value != null && !value.isBlank()) {
            String prefix = SPACER.repeat(layer) + title + ": ";
            String[] lines = value.split("\n", -1);
            System.out.println(prefix + lines[0]);
            for (int i = 1; i < lines.length; i++) {
                System.out.println(" ".repeat(prefix.length()) + lines[i]);
            }
        }
    }

    private String formatInstruments(final int vanilla, final int custom) {
        if (custom < 1) return "%,d".formatted(vanilla);
        if (vanilla < 1) return "%,d".formatted(custom);
        return "%,d (%,d vanilla, %,d custom)".formatted(vanilla + custom, vanilla, custom);
    }

}
