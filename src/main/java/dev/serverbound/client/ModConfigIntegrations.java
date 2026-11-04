package dev.serverbound.client;

import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Small, optional reload hooks for mods whose config files are profile-managed. */
final class ModConfigIntegrations {
    private ModConfigIntegrations() { }

    static void reloadProfileManagedConfigs() {
        reloadMidnightConfigs();
        reloadAutoConfigs();
        reloadZoomify();
        reloadHealthIndicators();
        reloadCombatHitboxes();
    }

    /** Reload every config registered through MidnightLib, including Reach Display. */
    private static void reloadMidnightConfigs() {
        try {
            Class<?> midnightConfig = Class.forName("eu.midnightdust.lib.config.MidnightConfig");
            Field instancesField = midnightConfig.getField("configInstances");
            Object instancesValue = instancesField.get(null);
            if (!(instancesValue instanceof Map<?, ?> instances)) return;
            Method loadValues = midnightConfig.getMethod("loadValuesFromJson");
            for (Object config : instances.values()) {
                if (config == null) continue;
                try { loadValues.invoke(config); }
                catch (ReflectiveOperationException | RuntimeException e) {
                    LoggerFactory.getLogger("Serverbound").warn("Could not reload a MidnightLib config", e);
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
            // MidnightLib is not installed.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not reload MidnightLib configs", e);
        }
    }

    /** Reload every config registered through Cloth Config's AutoConfig API. */
    private static void reloadAutoConfigs() {
        try {
            Class<?> autoConfig = Class.forName("me.shedaniel.autoconfig.AutoConfig");
            Field holdersField = autoConfig.getDeclaredField("holders");
            holdersField.setAccessible(true);
            Object holdersValue = holdersField.get(null);
            if (!(holdersValue instanceof Map<?, ?> holders)) return;
            for (Object holder : holders.values()) {
                if (holder == null) continue;
                try { holder.getClass().getMethod("load").invoke(holder); }
                catch (ReflectiveOperationException | RuntimeException e) {
                    LoggerFactory.getLogger("Serverbound").warn("Could not reload an AutoConfig setting", e);
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            // Cloth Config / AutoConfig is not installed.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LoggerFactory.getLogger("Serverbound").warn("Could not reload AutoConfig settings", e);
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
