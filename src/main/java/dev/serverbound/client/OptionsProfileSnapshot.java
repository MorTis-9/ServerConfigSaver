package dev.serverbound.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Copies vanilla options while keeping resource-pack selections shared across servers. */
final class OptionsProfileSnapshot {
    private static final String[] SHARED_KEYS = {"resourcePacks:", "incompatibleResourcePacks:"};

    private OptionsProfileSnapshot() { }

    static void save(Path liveOptions, Path snapshot) throws IOException {
        List<String> saved = withoutPackSelections(Files.readAllLines(liveOptions, StandardCharsets.UTF_8));
        if (Files.isRegularFile(snapshot)
                && Files.readAllLines(snapshot, StandardCharsets.UTF_8).equals(saved)) return;
        write(snapshot, saved);
    }

    /** Apply profile options but retain the current global resource-pack selection. */
    static void apply(Path profileOptions, Path liveOptions) throws IOException {
        List<String> live = Files.exists(liveOptions)
            ? Files.readAllLines(liveOptions, StandardCharsets.UTF_8) : List.of();
        List<String> result = withoutPackSelections(Files.readAllLines(profileOptions, StandardCharsets.UTF_8));
        for (String line : live) if (isPackSelection(line)) result.add(line);
        write(liveOptions, result);
    }

    private static List<String> withoutPackSelections(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) if (!isPackSelection(line)) result.add(line);
        return result;
    }

    private static boolean isPackSelection(String line) {
        for (String key : SHARED_KEYS) if (line.startsWith(key)) return true;
        return false;
    }

    private static void write(Path target, List<String> lines) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".serverbound-tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
