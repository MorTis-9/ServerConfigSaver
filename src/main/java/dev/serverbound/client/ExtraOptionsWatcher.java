package dev.serverbound.client;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** Snapshots top-level options*.txt files used by some mods outside the config folder. */
final class ExtraOptionsWatcher implements AutoCloseable {
    private final Path gameDirectory;
    private final Path snapshotDirectory;
    private final BiConsumer<Path, Boolean> changeListener;
    private final WatchService watcher;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    ExtraOptionsWatcher(Path gameDirectory, Path snapshotDirectory, BiConsumer<Path, Boolean> changeListener) throws IOException {
        this.gameDirectory = gameDirectory;
        this.snapshotDirectory = snapshotDirectory;
        this.changeListener = changeListener;
        Files.createDirectories(gameDirectory);
        Files.createDirectories(snapshotDirectory);
        watcher = FileSystems.getDefault().newWatchService();
        gameDirectory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        thread = new Thread(this::watch, "Serverbound-extra-options-snapshot");
        thread.setDaemon(true);
        thread.start();
    }

    static void copyCurrent(Path gameDirectory, Path snapshotDirectory) throws IOException {
        Files.createDirectories(snapshotDirectory);
        try (var files = Files.list(gameDirectory)) {
            for (Path file : files.filter(ExtraOptionsWatcher::isExtraOption).toList())
                if (Files.isRegularFile(file)) ConfigSnapshotWatcher.atomicCopy(file, snapshotDirectory.resolve(file.getFileName()));
        }
        try (var saved = Files.list(snapshotDirectory)) {
            for (Path file : saved.toList()) {
                if (isExtraOption(file) && !Files.isRegularFile(gameDirectory.resolve(file.getFileName())))
                    Files.deleteIfExists(file);
            }
        }
    }

    static void restore(Path gameDirectory, Path snapshotDirectory) throws IOException {
        Files.createDirectories(gameDirectory);
        try (var files = Files.list(gameDirectory)) {
            for (Path file : files.filter(ExtraOptionsWatcher::isExtraOption).toList()) Files.deleteIfExists(file);
        }
        overlay(snapshotDirectory, gameDirectory);
    }

    static void overlay(Path snapshotDirectory, Path gameDirectory) throws IOException {
        if (!Files.exists(snapshotDirectory)) return;
        Files.createDirectories(gameDirectory);
        try (var files = Files.list(snapshotDirectory)) {
            for (Path file : files.filter(ExtraOptionsWatcher::isExtraOption).toList())
                if (Files.isRegularFile(file)) ConfigSnapshotWatcher.atomicCopy(file, gameDirectory.resolve(file.getFileName()));
        }
    }

    private void watch() {
        while (running.get()) {
            try {
                WatchKey key = watcher.take();
                for (var event : key.pollEvents()) {
                    Path file = gameDirectory.resolve((Path) event.context());
                    if (!isExtraOption(file)) continue;
                    Path snapshot = snapshotDirectory.resolve(file.getFileName());
                    try {
                        boolean deleted = event.kind() == StandardWatchEventKinds.ENTRY_DELETE;
                        if (deleted) Files.deleteIfExists(snapshot);
                        else if (Files.isRegularFile(file)) ConfigSnapshotWatcher.atomicCopy(file, snapshot);
                        else continue;
                        changeListener.accept(file.getFileName(), deleted);
                    } catch (IOException ignored) { /* A later event or disconnect snapshot retries the copy. */ }
                }
                if (!key.reset()) break;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            catch (Exception e) { break; }
        }
    }

    private static boolean isExtraOption(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.startsWith("options") && name.endsWith(".txt") && !name.equals("options.txt");
    }

    @Override public void close() {
        running.set(false);
        try { watcher.close(); } catch (IOException ignored) { }
        thread.interrupt();
    }

    void awaitTermination() throws InterruptedException { thread.join(); }
}
