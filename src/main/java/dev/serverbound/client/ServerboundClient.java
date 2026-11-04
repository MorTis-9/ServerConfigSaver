package dev.serverbound.client;

import dev.serverbound.client.mixin.ScreenAccessor;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ServerboundClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("serverbound", "main"));

    @Override
    public void onInitializeClient() {
        ProfileManager.initialize(net.minecraft.client.Minecraft.getInstance());
        KeyMapping openProfiles = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.serverbound.open_profiles", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openProfiles.consumeClick()) {
                if (!(client.screen instanceof ProfileScreen)) client.setScreen(new ProfileScreen(client.screen));
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof JoinMultiplayerScreen)) return;
            Button toggle = Button.builder(toggleLabel(), button -> {
                ProfileManager.toggle(client);
                button.setMessage(toggleLabel());
            }).bounds(Math.max(6, scaledWidth - 102), screen instanceof TitleScreen
                ? Math.max(6, scaledHeight - 24) : Math.max(6, scaledHeight - 100), 96, 18).build();
            ((ScreenAccessor) screen).serverbound$addRenderableWidget(toggle);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ProfileManager.activate(client, handler.getConnection().getRemoteAddress()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ProfileManager.deactivate(client));
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            private int ticks;
            @Override public void onEndTick(net.minecraft.client.Minecraft client) {
                if (++ticks >= 20) {
                    ticks = 0;
                    ProfileManager.autosave(client);
                    ProfileManager.onOptionsSaved(client);
                }
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("serverbound")
            .then(literal("status").executes(ctx -> ProfileManager.report(ctx.getSource().getClient())))
            .then(literal("toggle").executes(ctx -> ProfileManager.toggle(ctx.getSource().getClient())))
            .then(literal("reset").executes(ctx -> ProfileManager.reset(ctx.getSource().getClient())))
            .then(literal("copy").then(argument("server", StringArgumentType.word()).executes(ctx -> ProfileManager.copy(ctx.getSource().getClient(), StringArgumentType.getString(ctx, "server")))))));
    }

    private static Component toggleLabel() {
        return Component.translatable(ProfileManager.enabled()
            ? "serverbound.button.profiles_enabled" : "serverbound.button.profiles_disabled");
    }
}
