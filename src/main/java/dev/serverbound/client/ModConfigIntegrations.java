package dev.serverbound.client;

import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Small, optional reload hooks for mods whose config files are profile-managed. */
final class ModConfigIntegrations {
    private ModConfigIntegrations() { }

    static void reloadProfileManagedConfigs() {
        reloadReachDisplay();
        reloadGammaUtils();
        refreshGammaUtilsRuntime();
        reloadZoomify();
        reloadHealthIndicators();
        reloadCombatHitboxes();
    }

    private static void reloadReachDisplay() {
        try {
            Class<?> midnightConfig = Class.forName("eu.midnightdust.lib.config.MidnightConfig");
            Field instancesField = midnightConfig.getField("configInstances");
            Object instancesValue = instancesField.get(null);
            if (!(instancesValue instanceof Map<?, ?> instances)) return;
            Object reachDisplay = instances.get("reach_display");
            if (reachDisplay == null) return;
            Method loadValues = midnightConfig.getMethod("loadValuesFromJson");
            loadValues.invoke(reachDisplay);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            // Reach Display or MidnightLib is not installed.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not reload Reach Display settings", e);
        }
    }

    /** Reload Gamma Utils only; reloading every AutoConfig holder can disrupt unrelated mods. */
    private static void reloadGammaUtils() {
        try {
            Class<?> gammaUtils = Class.forName("io.github.sjouwer.gammautils.GammaUtils");
            Field holderField = gammaUtils.getDeclaredField("configHolder");
            holderField.setAccessible(true);
            Object holder = holderField.get(null);
            if (holder == null) return;
            holder.getClass().getMethod("load").invoke(holder);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            // Gamma Utils or this version's config API is not installed.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not reload Gamma Utils config", e);
        }
    }

    /** Zoomify stores settings in the YACL v3 singleton and exposes file reload there. */
    private static void reloadZoomify() {
        try {
            Class<?> settings = Class.forName("dev.isxander.zoomify.config.ZoomifySettings");
            Field companionField = settings.getField("Companion");
            Object settingsInstance = companionField.get(null);
            settings.getMethod("loadFromFile").invoke(settingsInstance);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            // Zoomify is not installed, or this version has no reload method.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not reload Zoomify settings", e);
        }
    }

    /** Gamma Utils caches nested settings in static manager fields after startup. */
    private static void refreshGammaUtilsRuntime() {
        try {
            Class<?> gammaUtils = Class.forName("io.github.sjouwer.gammautils.GammaUtils");
            Object config = gammaUtils.getMethod("getConfig").invoke(null);
            Object gammaSettings = config.getClass().getField("gamma").get(config);
            Object nightVisionSettings = config.getClass().getField("nightVision").get(config);
            copySettings(gammaSettings, "io.github.sjouwer.gammautils.GammaManager", "gamma");
            copySettings(nightVisionSettings, "io.github.sjouwer.gammautils.NightVisionManager", "nightVision");
            gammaUtils.getMethod("saveConfig").invoke(null);
            Class.forName("io.github.sjouwer.gammautils.GammaManager").getMethod("setDynamicGamma").invoke(null);
            Class.forName("io.github.sjouwer.gammautils.NightVisionManager").getMethod("setDynamicNightVision").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) {
            // Gamma Utils is not installed, or this version has a different runtime API.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not apply Gamma Utils settings", e);
        }
    }

    private static void copySettings(Object source, String managerName, String fieldName)
            throws ReflectiveOperationException {
        if (source == null) return;
        Class<?> manager = Class.forName(managerName);
        Field cachedField = manager.getDeclaredField(fieldName);
        cachedField.setAccessible(true);
        Object cached = cachedField.get(null);
        if (cached == null || !cached.getClass().equals(source.getClass())) return;
        for (Field setting : source.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(setting.getModifiers())
                    || java.lang.reflect.Modifier.isFinal(setting.getModifiers())) continue;
            setting.setAccessible(true);
            setting.set(cached, setting.get(source));
        }
    }

    private static void reloadHealthIndicators() {
        try {
            Class<?> config = Class.forName("fabric.io.github.adytech99.healthindicators.config.ModConfig");
            Object handler = config.getField("HANDLER").get(null);
            Class<?> handlerApi = Class.forName("dev.isxander.yacl3.config.v2.api.ConfigClassHandler");
            handlerApi.getMethod("load").invoke(handler);

            Class<?> simpleConfig = Class.forName("fabric.io.github.adytech99.healthindicators.config.Config");
            simpleConfig.getMethod("load").invoke(null);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            // Health Indicators or its config API is not installed.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not reload Health Indicators settings", e);
        }
    }

    private static void reloadCombatHitboxes() {
        try {
            Class<?> config = Class.forName("me.sootysplash.box.Config");
            Field instanceField = config.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
            config.getMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            // Combat Hitboxes or its config API is not installed.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not reload Combat Hitboxes settings", e);
        }
    }
}
