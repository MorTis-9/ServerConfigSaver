package dev.serverbound.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Properties;

/** Manages the vanilla options and best-effort snapshots of third-party config files. */
public final class ProfileManager {
    private static final String ROOT = "serverbound";
    private static final String PROFILES = ROOT + "/profiles";
    private static String activeId;
    private static String activeLabel;
    private static SocketAddress connectedRemote;
    private static boolean enabled = true;
    private static volatile boolean syncAllProfiles;
    private static ConfigSnapshotWatcher configWatcher;
    private static ExtraOptionsWatcher extraOptionsWatcher;

    private ProfileManager() { }

    public static synchronized void initialize(Minecraft client) {
        Path settings = root(client).resolve("settings.properties");
        if (Files.exists(settings)) {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(settings)) {
                properties.load(input);
                enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
                syncAllProfiles = Boolean.parseBoolean(properties.getProperty("syncAllProfiles", "false"));
            } catch (IOException e) { log("Could not read settings", e); }
        }
        Path marker = root(client).resolve("active-profile.txt");
        if (Files.exists(marker)) {
            restoreSharedSettings(client);
            try { Files.deleteIfExists(marker); } catch (IOException e) { log("Could not clear session marker", e); }
        }
        try { captureDefaults(client); startDefaultWatcher(client); }
        catch (IOException e) { log("Could not initialize shared settings snapshot", e); }
    }

    private static void saveSettings(Minecraft client) {
        Path settings = root(client).resolve("settings.properties");
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("syncAllProfiles", Boolean.toString(syncAllProfiles));
        try {
            Files.createDirectories(settings.getParent());
            try (var output = Files.newOutputStream(settings)) { properties.store(output, "Serverbound preferences"); }
        } catch (IOException e) { log("Could not save settings", e); }
    }

    public static synchronized void activate(Minecraft client, SocketAddress remote) {
        connectedRemote = remote;
        if (!enabled || !(remote instanceof InetSocketAddress)) return;
        stopWatcher();
        String socketId = identify(remote);
        activeId = identify(client, remote);
        activeLabel = remote.toString();
        Path profile = profileDir(client);
        try {
            // Earlier builds identified profiles by the resolved socket IP.
            // Prefer the address the player entered, and carry an old IP-based
            // profile forward when no profile for that address exists yet.
            Path previousProfile = profilesDir(client).resolve(socketId);
            if (!profile.equals(previousProfile) && !Files.exists(profile) && Files.exists(previousProfile)) {
                try { Files.move(previousProfile, profile); }
                catch (IOException e) { log("Could not migrate the previous IP-based profile", e); }
            }
            Files.createDirectories(profile);
            captureDefaults(client);
            Files.createDirectories(root(client));
            Files.writeString(root(client).resolve("active-profile.txt"), activeId);
            Path options = profile.resolve("options.txt");
            if (Files.exists(options)) {
                OptionsProfileSnapshot.apply(options, optionsFile(client));
                client.options.load();
            } else persistOptions(client);

            Path profileExtraOptions = profile.resolve("root-options");
            if (Files.exists(profileExtraOptions)) {
                ExtraOptionsWatcher.restore(client.gameDirectory.toPath(), root(client).resolve("defaults/root-options"));
                ExtraOptionsWatcher.overlay(profileExtraOptions, client.gameDirectory.toPath());
            } else ExtraOptionsWatcher.copyCurrent(client.gameDirectory.toPath(), profileExtraOptions);

            Path profileConfig = profile.resolve("config");
            if (!Files.exists(profileConfig)) ConfigSnapshotWatcher.copyTree(configDir(), profileConfig);
            else {
                ConfigSnapshotWatcher.clearSettingsTree(configDir());
                ConfigSnapshotWatcher.copyTree(root(client).resolve("defaults/config"), configDir());
                ConfigSnapshotWatcher.copyTree(profileConfig, configDir());
                ModConfigIntegrations.reloadProfileManagedConfigs();
            }
            configWatcher = new ConfigSnapshotWatcher(configDir(), profileConfig,
                (relative, deleted) -> mirrorConfigChange(client, relative, deleted));
            extraOptionsWatcher = new ExtraOptionsWatcher(client.gameDirectory.toPath(), profileExtraOptions,
                (name, deleted) -> mirrorRootOptionChange(client, name, deleted));
            message(client, "serverbound.message.profile_activated", activeLabel);
        } catch (Exception e) {
            log("Could not activate profile", e);
            message(client, "serverbound.message.profile_load_failed");
            stopWatcher();
            activeId = null;
            clearMarker(client);
            startDefaultWatcher(client);
        }
    }

    public static synchronized void deactivate(Minecraft client) {
        if (activeId == null) { connectedRemote = null; return; }
        stopWatcher();
        persist(client);
        restoreSharedSettings(client);
        clearMarker(client);
        activeId = null;
        activeLabel = null;
        connectedRemote = null;
        startDefaultWatcher(client);
    }

    public static synchronized void onOptionsSaved(Minecraft client) {
        persistOptions(client);
    }

    public static synchronized void autosave(Minecraft client) {
        if (activeId != null && enabled) client.options.save();
    }

    public static synchronized int toggle(Minecraft client) {
        if (enabled) {
            stopWatcher();
            persist(client);
            // If profiles are disabled while connected, first return to the
            // shared snapshot so server-specific values don't become the new
            // shared defaults.
            if (activeId != null) restoreSharedSettings(client);
            try { refreshDefaults(client); } catch (IOException e) { log("Could not update shared defaults", e); }
            enabled = false;
            saveSettings(client);
            clearMarker(client);
            activeId = null;
            activeLabel = null;
            startDefaultWatcher(client);
            message(client, "serverbound.message.profiles_disabled");
        } else {
            enabled = true;
            saveSettings(client);
            if (connectedRemote instanceof InetSocketAddress) activate(client, connectedRemote);
            else message(client, "serverbound.message.profiles_enabled");
        }
        return 1;
    }

    public static synchronized boolean syncAllProfiles() { return syncAllProfiles; }

    public static synchronized int toggleSyncAllProfiles(Minecraft client) {
        syncAllProfiles = !syncAllProfiles;
        saveSettings(client);
        message(client, syncAllProfiles ? "serverbound.message.sync_all_enabled" : "serverbound.message.sync_all_disabled");
        return 1;
    }

    public static synchronized int report(Minecraft client) {
        if (!enabled) message(client, "serverbound.message.status_disabled");
        else if (activeLabel == null) message(client, "serverbound.message.status_no_profile");
        else message(client, "serverbound.message.status_active", activeLabel);
        return 1;
    }

    public static synchronized int reset(Minecraft client) {
        if (activeId == null) { message(client, "serverbound.message.reset_requires_server"); return 0; }
        stopWatcher();
        try {
            Path defaults = root(client).resolve("defaults");
            Path defaultOptions = defaults.resolve("options.txt");
            if (Files.exists(defaultOptions)) {
                OptionsProfileSnapshot.apply(defaultOptions, optionsFile(client));
                client.options.load();
            }
            Path defaultConfig = defaults.resolve("config");
            ConfigSnapshotWatcher.clearSettingsTree(configDir());
            ConfigSnapshotWatcher.copyTree(defaultConfig, configDir());
            ModConfigIntegrations.reloadProfileManagedConfigs();
            ExtraOptionsWatcher.restore(client.gameDirectory.toPath(), defaults.resolve("root-options"));
            Path profile = profileDir(client);
            OptionsProfileSnapshot.save(optionsFile(client), profile.resolve("options.txt"));
            ConfigSnapshotWatcher.deleteTree(profile.resolve("config"));
            ConfigSnapshotWatcher.copyTree(configDir(), profile.resolve("config"));
            ExtraOptionsWatcher.copyCurrent(client.gameDirectory.toPath(), profile.resolve("root-options"));
            configWatcher = new ConfigSnapshotWatcher(configDir(), profile.resolve("config"),
                (relative, deleted) -> mirrorConfigChange(client, relative, deleted));
            extraOptionsWatcher = new ExtraOptionsWatcher(client.gameDirectory.toPath(), profile.resolve("root-options"),
                (name, deleted) -> mirrorRootOptionChange(client, name, deleted));
            message(client, "serverbound.message.reset_success");
            return 1;
        } catch (IOException e) { log("Could not reset profile", e); message(client, "serverbound.message.reset_failed"); return 0; }
    }

    public static synchronized int copy(Minecraft client, String destination) {
        if (activeId == null) { message(client, "serverbound.message.copy_requires_server"); return 0; }
        String target;
        try { target = identifyProfile(destination); }
        catch (IllegalArgumentException e) {
            message(client, "serverbound.message.copy_invalid_destination");
            return 0;
        }
        if (target.equals(activeId)) { message(client, "serverbound.message.copy_same_profile"); return 0; }
        stopWatcher();
        try {
            persist(client);
            Path targetDir = profilesDir(client).resolve(target);
            ConfigSnapshotWatcher.deleteTree(targetDir);
            ConfigSnapshotWatcher.copyTree(profileDir(client), targetDir);
            configWatcher = new ConfigSnapshotWatcher(configDir(), profileDir(client).resolve("config"),
                (relative, deleted) -> mirrorConfigChange(client, relative, deleted));
            extraOptionsWatcher = new ExtraOptionsWatcher(client.gameDirectory.toPath(), profileDir(client).resolve("root-options"),
                (name, deleted) -> mirrorRootOptionChange(client, name, deleted));
            message(client, "serverbound.message.copy_success", target);
            return 1;
        } catch (IOException e) { log("Could not copy profile", e); message(client, "serverbound.message.copy_failed"); return 0; }
    }

    public static synchronized boolean enabled() { return enabled; }
    public static synchronized boolean hasActiveProfile() { return activeId != null; }
    public static synchronized Component statusText() {
        return !enabled ? Component.translatable("serverbound.status.disabled")
            : activeLabel == null ? Component.translatable("serverbound.status.no_profile")
            : Component.translatable("serverbound.status.active", activeLabel);
    }
    public static synchronized String currentProfileId() { return activeId == null ? "" : activeId; }

    private static void persist(Minecraft client) {
        persistOptions(client);
        if (activeId == null) return;
        try { ConfigSnapshotWatcher.syncTree(configDir(), profileDir(client).resolve("config")); }
        catch (IOException e) { log("Could not save mod config snapshot", e); }
        try { ExtraOptionsWatcher.copyCurrent(client.gameDirectory.toPath(), profileDir(client).resolve("root-options")); }
        catch (IOException e) { log("Could not save extra mod options", e); }
        if (syncAllProfiles) {
            broadcastConfigTree(client);
            broadcastRootOptions(client);
        }
    }

    private static void persistOptions(Minecraft client) {
        Path currentSnapshot = activeId == null
            ? root(client).resolve("defaults/options.txt")
            : profileDir(client).resolve("options.txt");
        try { OptionsProfileSnapshot.save(optionsFile(client), currentSnapshot); }
        catch (IOException e) { log("Could not save vanilla options", e); }
        if (syncAllProfiles) broadcastOptions(client);
    }

    private static void captureDefaults(Minecraft client) throws IOException {
        Path defaults = root(client).resolve("defaults");
        Files.createDirectories(defaults);
        if (!Files.exists(defaults.resolve("options.txt")) && Files.exists(optionsFile(client)))
            OptionsProfileSnapshot.save(optionsFile(client), defaults.resolve("options.txt"));
        if (!Files.exists(defaults.resolve("config"))) {
            Files.createDirectories(defaults.resolve("config"));
            ConfigSnapshotWatcher.copyTree(configDir(), defaults.resolve("config"));
        }
        if (!Files.exists(defaults.resolve("root-options")))
            ExtraOptionsWatcher.copyCurrent(client.gameDirectory.toPath(), defaults.resolve("root-options"));
    }

    private static void refreshDefaults(Minecraft client) throws IOException {
        Path defaults = root(client).resolve("defaults");
        Files.createDirectories(defaults);
        OptionsProfileSnapshot.save(optionsFile(client), defaults.resolve("options.txt"));
        ConfigSnapshotWatcher.deleteTree(defaults.resolve("config"));
        Files.createDirectories(defaults.resolve("config"));
        ConfigSnapshotWatcher.copyTree(configDir(), defaults.resolve("config"));
        ConfigSnapshotWatcher.deleteTree(defaults.resolve("root-options"));
        ExtraOptionsWatcher.copyCurrent(client.gameDirectory.toPath(), defaults.resolve("root-options"));
    }

    private static void startDefaultWatcher(Minecraft client) {
        if (configWatcher != null || activeId != null) return;
        try {
            configWatcher = new ConfigSnapshotWatcher(configDir(), root(client).resolve("defaults/config"),
                (relative, deleted) -> mirrorConfigChange(client, relative, deleted));
            extraOptionsWatcher = new ExtraOptionsWatcher(client.gameDirectory.toPath(), root(client).resolve("defaults/root-options"),
                (name, deleted) -> mirrorRootOptionChange(client, name, deleted));
        }
        catch (IOException e) { log("Could not watch shared mod settings", e); }
    }

    private static void clearMarker(Minecraft client) {
        try { Files.deleteIfExists(root(client).resolve("active-profile.txt")); }
        catch (IOException e) { log("Could not clear session marker", e); }
    }

    private static void restoreSharedSettings(Minecraft client) {
        Path defaults = root(client).resolve("defaults");
        try {
            Path defaultOptions = defaults.resolve("options.txt");
            if (Files.exists(defaultOptions)) {
                OptionsProfileSnapshot.apply(defaultOptions, optionsFile(client));
                // During startup recovery Minecraft has not constructed Options yet;
                // the normal client initialization will read the restored file.
                if (client.options != null) client.options.load();
            }
            Path defaultConfig = defaults.resolve("config");
            if (Files.exists(defaultConfig)) {
                ConfigSnapshotWatcher.clearSettingsTree(configDir());
                ConfigSnapshotWatcher.copyTree(defaultConfig, configDir());
                if (client.options != null) ModConfigIntegrations.reloadProfileManagedConfigs();
            }
            ExtraOptionsWatcher.restore(client.gameDirectory.toPath(), defaults.resolve("root-options"));
        } catch (IOException e) { log("Could not restore shared settings", e); }
    }

    private static void stopWatcher() {
        if (configWatcher != null) { configWatcher.close(); configWatcher = null; }
        if (extraOptionsWatcher != null) { extraOptionsWatcher.close(); extraOptionsWatcher = null; }
    }

    private static void broadcastOptions(Minecraft client) {
        Path live = optionsFile(client);
        try { OptionsProfileSnapshot.save(live, root(client).resolve("defaults/options.txt")); }
        catch (IOException e) { log("Could not copy shared options to all profiles", e); }
        Path profiles = profilesDir(client);
        if (!Files.isDirectory(profiles)) return;
        try (var directories = Files.list(profiles)) {
            for (Path profile : directories.filter(Files::isDirectory).toList()) {
                try { OptionsProfileSnapshot.save(live, profile.resolve("options.txt")); }
                catch (IOException e) { log("Could not update options for profile " + profile.getFileName(), e); }
            }
        } catch (IOException e) { log("Could not enumerate server profiles", e); }
    }

    private static void mirrorConfigChange(Minecraft client, Path relative, boolean deleted) {
        if (!syncAllProfiles || relative == null || relative.toString().isBlank()) return;
        Path source = configDir().resolve(relative);
        mirrorFile(source, root(client).resolve("defaults/config").resolve(relative), deleted);
        Path profiles = profilesDir(client);
        if (!Files.isDirectory(profiles)) return;
        try (var directories = Files.list(profiles)) {
            for (Path profile : directories.filter(Files::isDirectory).toList())
                mirrorFile(source, profile.resolve("config").resolve(relative), deleted);
        } catch (IOException e) { log("Could not mirror a changed config to all profiles", e); }
    }

    private static void mirrorRootOptionChange(Minecraft client, Path name, boolean deleted) {
        if (!syncAllProfiles || name == null || name.getNameCount() != 1) return;
        Path source = client.gameDirectory.toPath().resolve(name);
        mirrorFile(source, root(client).resolve("defaults/root-options").resolve(name), deleted);
        Path profiles = profilesDir(client);
        if (!Files.isDirectory(profiles)) return;
        try (var directories = Files.list(profiles)) {
            for (Path profile : directories.filter(Files::isDirectory).toList())
                mirrorFile(source, profile.resolve("root-options").resolve(name), deleted);
        } catch (IOException e) { log("Could not mirror a mod options file to all profiles", e); }
    }

    private static void mirrorFile(Path source, Path target, boolean deleted) {
        try {
            if (deleted || !Files.isRegularFile(source)) {
                Files.deleteIfExists(target);
            } else if (!Files.isRegularFile(target) || Files.mismatch(source, target) != -1) {
                ConfigSnapshotWatcher.atomicCopy(source, target);
            }
        } catch (IOException e) { log("Could not mirror a settings file to all profiles", e); }
    }

    private static void broadcastConfigTree(Minecraft client) {
        Path profiles = profilesDir(client);
        if (!Files.isDirectory(profiles)) return;
        try (var directories = Files.list(profiles)) {
            for (Path profile : directories.filter(Files::isDirectory).toList())
                copySettingsTree(configDir(), profile.resolve("config"), profile.getFileName().toString());
        } catch (IOException e) { log("Could not enumerate server profiles for settings sync", e); }
        copySettingsTree(configDir(), root(client).resolve("defaults/config"), "shared");
    }

    private static void copySettingsTree(Path source, Path target, String label) {
        try {
            ConfigSnapshotWatcher.syncTree(source, target);
        } catch (IOException e) { log("Could not sync settings for " + label, e); }
    }

    private static void broadcastRootOptions(Minecraft client) {
        Path game = client.gameDirectory.toPath();
        try { ExtraOptionsWatcher.copyCurrent(game, root(client).resolve("defaults/root-options")); }
        catch (IOException e) { log("Could not sync shared mod options", e); }
        Path profiles = profilesDir(client);
        if (!Files.isDirectory(profiles)) return;
        try (var directories = Files.list(profiles)) {
            for (Path profile : directories.filter(Files::isDirectory).toList()) {
                try { ExtraOptionsWatcher.copyCurrent(game, profile.resolve("root-options")); }
                catch (IOException e) { log("Could not sync mod options for " + profile.getFileName(), e); }
            }
        } catch (IOException e) { log("Could not enumerate server profiles for mod options sync", e); }
    }

    private static Path root(Minecraft client) { return client.gameDirectory.toPath().resolve(ROOT); }
    private static Path profilesDir(Minecraft client) { return root(client).resolve("profiles"); }
    private static Path profileDir(Minecraft client) { return profilesDir(client).resolve(activeId); }
    private static Path optionsFile(Minecraft client) { return client.gameDirectory.toPath().resolve("options.txt"); }
    private static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    private static String identify(SocketAddress address) {
        String host;
        int port = 25565;
        if (address instanceof InetSocketAddress inet) { host = inet.getHostString().toLowerCase(Locale.ROOT); port = inet.getPort(); }
        else host = address.toString().toLowerCase(Locale.ROOT);
        return normalize(host + "-" + port);
    }

    private static String identify(Minecraft client, SocketAddress remote) {
        var server = client.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            try { return identifyProfile(server.ip); }
            catch (IllegalArgumentException e) { log("Could not read the configured server address", e); }
        }
        return identify(remote);
    }

    /** Accepts a bare server address, address:port, [IPv6]:port, or an existing host-port profile id. */
    private static String identifyProfile(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Empty server address");

        String host = value;
        int port = 25565;
        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket < 0) throw new IllegalArgumentException("Invalid IPv6 address");
            host = value.substring(1, closingBracket);
            String suffix = value.substring(closingBracket + 1);
            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":")) throw new IllegalArgumentException("Invalid server address");
                port = parsePort(suffix.substring(1));
            }
        } else {
            long colons = value.chars().filter(ch -> ch == ':').count();
            if (colons == 1) {
                int separator = value.lastIndexOf(':');
                host = value.substring(0, separator);
                port = parsePort(value.substring(separator + 1));
            } else if (colons == 0) {
                int separator = value.lastIndexOf('-');
                if (separator > 0 && value.substring(separator + 1).matches("\\d{1,5}")) {
                    host = value.substring(0, separator);
                    port = parsePort(value.substring(separator + 1));
                }
            }
        }

        if (host.isBlank()) throw new IllegalArgumentException("Empty server address");
        String id = normalize(host + "-" + port);
        if (id.isBlank() || id.equals(".") || id.equals("..")) throw new IllegalArgumentException("Invalid server address");
        return id;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port", e);
        }
    }

    private static String normalize(String value) {
        String safe = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
            .replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }

    private static void message(Minecraft client, String translationKey, Object... args) {
        if (client.player != null) client.player.displayClientMessage(
            Component.literal("[ServerConfigSaver] ").append(Component.translatable(translationKey, args)), false);
    }

    private static void log(String text, Throwable e) { LoggerFactory.getLogger("Serverbound").error(text, e); }
}
