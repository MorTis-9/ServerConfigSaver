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
