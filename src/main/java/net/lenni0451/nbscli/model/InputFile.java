package net.lenni0451.nbscli.model;

import net.lenni0451.nbscli.utils.IOUtils;

import java.io.File;
import java.nio.file.Path;

public record InputFile(File file, Path relativePath) {

    public static InputFile std(final File file) {
        return new InputFile(file, null);
    }


    public boolean isStd() {
        return IOUtils.isStd(this.file);
    }

    public String name() {
        return this.isStd() ? "STDIN" : this.file.getPath();
    }

    public File getOutputFile(final File outputFolder, final String extension) {
        String name = this.relativePath.getFileName().toString();
        if (extension != null) {
            name = IOUtils.getNameWithoutExtension(this.file) + "." + extension;
        }
        Path parent = this.relativePath.getParent();
        Path folder = parent == null ? outputFolder.toPath() : outputFolder.toPath().resolve(parent);
        return folder.resolve(name).toFile();
    }

}
