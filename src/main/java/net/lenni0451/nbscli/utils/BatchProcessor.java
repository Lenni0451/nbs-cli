package net.lenni0451.nbscli.utils;

import com.beust.jcommander.ParameterException;
import net.lenni0451.nbscli.Main;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class BatchProcessor {

    public static <T> void run(final List<T> items, final boolean failFast, final boolean showProgress, final Function<T, String> name, final Consumer<T> action) {
        if (items.size() == 1) {
            action.accept(items.get(0));
            return;
        }

        int failed = 0;
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            if (showProgress) {
                System.err.println("[" + (i + 1) + "/" + items.size() + "] " + name.apply(item));
            }
            try {
                action.accept(item);
            } catch (ParameterException e) {
                if (failFast) throw e;
                failed++;
                System.err.print("Error in " + name.apply(item) + ": ");
                Main.printError(e);
            }
        }
        if (failed > 0) {
            throw new ParameterException("Failed to process " + failed + " of " + items.size() + " files");
        }
    }

}
