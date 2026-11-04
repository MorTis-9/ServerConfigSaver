package dev.serverbound.client;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** Mirrors config-folder writes into the active server's snapshot while the game is running. */
final class ConfigSnapshotWatcher implements AutoCloseable {
    private final Path source;
    private final Path snapshot;
    private final BiConsumer<Path, Boolean> changeListener;
    private final WatchService watcher;
    private final Map<WatchKey, Path> directories = new ConcurrentHashMap<>();
    /** A file is mirrored only after the same size and timestamp were seen twice. */
    private final Map<Path, Fingerprint> observed = new HashMap<>();
    private final Map<Path, Fingerprint> mirrored = new HashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    ConfigSnapshotWatcher(Path source, Path snapshot, BiConsumer<Path, Boolean> changeListener) throws IOException {
        this.source = source;
        this.snapshot = snapshot;
        this.changeListener = changeListener;
        Files.createDirectories(source);
        Files.createDirectories(snapshot);
        watcher = FileSystems.getDefault().newWatchService();
        registerTree(source);
        thread = new Thread(this::watch, "Serverbound-config-snapshot");
        thread.setDaemon(true);
        thread.start();
    }

    private void registerTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isDirectory)
                    .filter(path -> !isExcluded(root.relativize(path))).toList()) {
                WatchKey key = path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, path);
            }
        }
    }

    private void watch() {
        while (running.get()) {
            try {
                // WatchService events are a fast hint, while the periodic scan also
                // catches OVERFLOW and filesystems that coalesce or miss events.
                WatchKey key = watcher.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (key != null) handleEvents(key);
                syncStableFiles();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            catch (Exception e) {
                if (running.get()) org.slf4j.LoggerFactory.getLogger("Serverbound")
                    .warn("Could not update active mod settings snapshot", e);
            }
        }
    }

    private void handleEvents(WatchKey key) {
        Path directory = directories.get(key);
        if (directory != null) {
            for (var event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                Path changed = directory.resolve((Path) event.context());
                if (isExcluded(source.relativize(changed))) continue;
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                    try { registerTree(changed); } catch (IOException ignored) { }
                }
            }
        }
        if (!key.reset()) directories.remove(key);
    }

    /**
     * Saves config-file changes throughout a server session, without copying a
     * file halfway through a mod's write. The recurring scan is the recovery path
     * for lost/coalesced filesystem events.
     */
    private void syncStableFiles() throws IOException {
        Set<Path> seen = new HashSet<>();
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                Path relative = source.relativize(file);
                if (isExcluded(relative)) continue;
                seen.add(relative);
                Fingerprint fingerprint = Fingerprint.read(file);
                Fingerprint previous = observed.put(relative, fingerprint);
                if (fingerprint.equals(previous) && !fingerprint.equals(mirrored.get(relative))) {
                    atomicCopy(file, snapshot.resolve(relative));
                    mirrored.put(relative, fingerprint);
                    changeListener.accept(relative, false);
                }
            }
        }
        observed.keySet().removeIf(path -> !seen.contains(path));
        for (Path stale : new HashSet<>(mirrored.keySet())) {
            if (!seen.contains(stale)) {
                Files.deleteIfExists(snapshot.resolve(stale));
                mirrored.remove(stale);
                changeListener.accept(stale, true);
            }
        }
    }

    private record Fingerprint(long size, FileTime modified) {
        private static Fingerprint read(Path path) throws IOException {
            return new Fingerprint(Files.size(path), Files.getLastModifiedTime(path));
        }
    }

    @Override public void close() {
        running.set(false);
        try { watcher.close(); } catch (IOException ignored) { }
        thread.interrupt();
    }

    static void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (isExcluded(relative)) continue;
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else if (Files.isRegularFile(path)) atomicCopy(path, destination);
            }
        }
    }

    /** Overlays settings without touching files whose contents are already correct. */
    static void overlayTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (isExcluded(relative)) continue;
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else if (Files.isRegularFile(path)) {
                    boolean differs = !Files.isRegularFile(destination)
                        || Files.size(path) != Files.size(destination)
                        || !Files.getLastModifiedTime(path).equals(Files.getLastModifiedTime(destination));
                    if (differs) atomicCopy(path, destination);
                }
            }
        }
    }

    /** Refreshes a snapshot without rewriting unchanged files and removes stale settings. */
    static void syncTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        Set<Path> present = new HashSet<>();
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                Path relative = source.relativize(file);
                if (isExcluded(relative)) continue;
                present.add(relative);
                Path saved = target.resolve(relative);
                boolean differs = !Files.isRegularFile(saved)
                    || Files.size(file) != Files.size(saved)
                    || !Files.getLastModifiedTime(file).equals(Files.getLastModifiedTime(saved));
                if (differs) atomicCopy(file, saved);
            }
        }
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path saved : paths.filter(Files::isRegularFile).toList()) {
                Path relative = target.relativize(saved);
                if (!isExcluded(relative) && !present.contains(relative)) Files.deleteIfExists(saved);
            }
        }
    }

    /** Clears profile-controlled config files while keeping runtime caches and databases intact. */
    static void clearSettingsTree(Path target) throws IOException {
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                if (path.equals(target) || isExcluded(target.relativize(path))) continue;
                try { Files.deleteIfExists(path); }
                catch (IOException ignored) { /* Locked runtime files should not abort the whole profile switch. */ }
            }
        }
    }

    private static boolean isExcluded(Path relative) {
        for (Path part : relative) {
            String name = part.toString().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("cache") || name.equals("caches") || name.equals("blob_cache")
                    || name.equals("blob-cache")) return true;
        }
        return false;
    }

    static void atomicCopy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".serverbound-tmp-" + Thread.currentThread().getId());
        Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
        try { Files.setLastModifiedTime(temp, Files.getLastModifiedTime(source)); }
        catch (IOException ignored) { }
        try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    static void deleteTree(Path target) throws IOException {
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) Files.deleteIfExists(path);
        }
    }
}
