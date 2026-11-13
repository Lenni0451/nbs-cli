package net.lenni0451.nbscli.utils;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.model.InputFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class IOUtils {

    public static boolean isStd(final File file) {
        return file.getPath().equals("-");
    }

    public static String getName(final File file, final String pipeName) {
        if (isStd(file)) {
            return pipeName;
        } else {
            return file.getName();
        }
    }

    public static void log(final File output, final String message) {
        if (isStd(output)) {
            System.err.println(message);
        } else {
            System.out.println(message);
        }
    }

    public static String getExtension(final File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex == -1 ? "" : name.substring(dotIndex + 1);
    }

    public static String getNameWithoutExtension(final File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex == -1 ? name : name.substring(0, dotIndex);
    }

    public static InputStream openInputStream(final File file) throws IOException {
        if (isStd(file)) {
            return new FileInputStream(FileDescriptor.in) {
                @Override
                public void close() {
                }
            };
        } else {
            return new FileInputStream(file);
        }
    }

    public static OutputStream openOutputStream(final File file) throws IOException {
        if (isStd(file)) {
            return new FileOutputStream(FileDescriptor.out) {
                @Override
                public void close() throws IOException {
                    this.flush();
                }
            };
        } else {
            createParentFolders(file);
            return new FileOutputStream(file);
        }
    }

    public static void createParentFolders(final File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
    }

    public static List<InputFile> resolveInputs(final List<File> inputs, final boolean recursive, final Predicate<File> folderFilter) {
        if (inputs.stream().anyMatch(IOUtils::isStd)) {
            if (inputs.size() > 1) throw new ParameterException("Standard input can't be combined with other inputs");
            return List.of(InputFile.std(inputs.get(0)));
        }

        Map<Path, InputFile> out = new LinkedHashMap<>();
        for (File input : inputs) {
            if (input.isFile()) {
                Path path = normalize(input.toPath());
                out.putIfAbsent(path, new InputFile(path.toFile(), path.getFileName()));
            } else if (input.isDirectory()) {
                Path root = normalize(input.toPath());
                try (Stream<Path> stream = Files.walk(root, recursive ? Integer.MAX_VALUE : 1)) {
                    stream
                            .filter(Files::isRegularFile)
                            .filter(path -> folderFilter.test(path.toFile()))
                            .map(IOUtils::normalize)
                            .sorted()
                            .forEach(path -> out.putIfAbsent(path, new InputFile(path.toFile(), root.relativize(path))));
                } catch (IOException | UncheckedIOException e) {
                    throw new ParameterException("Failed to list the files in folder: " + input.getAbsolutePath(), e);
                }
            } else {
                throw new ParameterException("Input file or folder does not exist: " + input.getAbsolutePath());
            }
        }
        return List.copyOf(out.values());
    }

    public static boolean isBatch(final List<File> inputs) {
        return inputs.size() > 1 || inputs.stream().anyMatch(File::isDirectory);
    }

    public static void validateOutputFolder(final File output) {
        if (isStd(output)) {
            throw new ParameterException("Can't write to standard output when processing multiple files or folders");
        }
        if (output.exists() && !output.isDirectory()) {
            throw new ParameterException("Output must be a folder when processing multiple files or folders: " + output.getAbsolutePath());
        }
    }

    public static void validateOutputFile(final File output, final boolean overwrite) {
        if (isStd(output)) return;
        if (output.isDirectory()) {
            throw new ParameterException("Output file is a directory: " + output.getAbsolutePath());
        }
        if (output.exists() && !overwrite) {
            throw new ParameterException("Output file already exists and overwrite is not allowed: " + output.getAbsolutePath());
        }
    }

    public static void validateOutputFiles(final Map<InputFile, File> outputs, final boolean overwrite) {
        Map<Path, InputFile> seen = new HashMap<>();
        outputs.forEach((input, output) -> {
            validateOutputFile(output, overwrite);
            InputFile other = seen.put(normalize(output.toPath()), input);
            if (other != null) {
                throw new ParameterException("Multiple inputs would be written to the same output file " + output.getPath() + ": " + other.file().getPath() + ", " + input.file().getPath());
            }
        });
    }

    public static Predicate<File> hasExtension(final String extension) {
        return file -> getExtension(file).equalsIgnoreCase(extension);
    }

    private static Path normalize(final Path path) {
        return path.toAbsolutePath().normalize();
    }

}
