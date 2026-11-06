package net.lenni0451.nbscli.utils;

import java.io.*;

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
            return new FileOutputStream(file);
        }
    }

}
