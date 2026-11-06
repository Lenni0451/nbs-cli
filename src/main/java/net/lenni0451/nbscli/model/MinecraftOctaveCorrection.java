package net.lenni0451.nbscli.model;

import net.raphimc.noteblocklib.format.minecraft.MinecraftDefinitions;
import net.raphimc.noteblocklib.model.note.Note;
import net.raphimc.noteblocklib.model.song.Song;

//TODO: Remove once its in NoteBlockLib
public enum MinecraftOctaveCorrection {

    NONE {
        @Override
        public void apply(final Note note) {
        }
    },
    INSTRUMENT_SHIFT {
        @Override
        public void apply(final Note note) {
            MinecraftDefinitions.instrumentShiftNote(note);
            MinecraftDefinitions.clampNoteKey(note);
        }
    },
    TRANSPOSE {
        @Override
        public void apply(final Note note) {
            MinecraftDefinitions.transposeNoteKey(note);
        }
    },
    CLAMP {
        @Override
        public void apply(final Note note) {
            MinecraftDefinitions.clampNoteKey(note);
        }
    };

    public abstract void apply(final Note note);

    public void apply(final Song song) {
        song.getNotes().forEach(this::apply);
    }

}
