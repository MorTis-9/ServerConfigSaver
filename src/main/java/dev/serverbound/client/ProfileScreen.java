package dev.serverbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProfileScreen extends Screen {
    private final Screen parent;
    private EditBox destination;

    public ProfileScreen(Screen parent) {
        super(Component.translatable("serverbound.screen.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int x = width / 2 - 110;
        int y = height / 2 - 22;
        addRenderableWidget(Button.builder(toggleLabel(), button -> {
            ProfileManager.toggle(minecraft);
            button.setMessage(toggleLabel());
        }).bounds(x, y - 44, 220, 20).build());

        addRenderableWidget(Button.builder(syncAllLabel(), button -> {
            ProfileManager.toggleSyncAllProfiles(minecraft);
            button.setMessage(syncAllLabel());
        }).bounds(x, y - 18, 220, 20).build());

        destination = new EditBox(font, x, y + 8, 220, 20, Component.translatable("serverbound.screen.destination"));
        destination.setMaxLength(100);
        destination.setHint(Component.translatable("serverbound.screen.destination_hint"));
        addRenderableWidget(destination);

        Button copy = Button.builder(Component.translatable("serverbound.screen.copy"), button -> {
            ProfileManager.copy(minecraft, destination.getValue());
        }).bounds(x, y + 34, 220, 20).build();
        copy.active = ProfileManager.hasActiveProfile();
        addRenderableWidget(copy);

        Button reset = Button.builder(Component.translatable("serverbound.screen.reset"), button -> {
            ProfileManager.reset(minecraft);
        }).bounds(x, y + 60, 220, 20).build();
        reset.active = ProfileManager.hasActiveProfile();
        addRenderableWidget(reset);

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(x, y + 88, 220, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, ProfileManager.statusText(), width / 2, height / 2 - 72, 0xFFFFFF);
        String profileId = ProfileManager.currentProfileId();
        if (!profileId.isEmpty()) graphics.drawCenteredString(font,
            Component.translatable("serverbound.screen.profile_id", profileId), width / 2, height / 2 - 58, 0xD0D0D0);
        graphics.drawCenteredString(font, Component.translatable("serverbound.screen.info_files"), width / 2, height / 2 + 112, 0xC0C0C0);
        graphics.drawCenteredString(font, Component.translatable("serverbound.screen.info_reload"), width / 2, height / 2 + 124, 0xC0C0C0);
        graphics.drawCenteredString(font, Component.translatable("serverbound.screen.info_sync_all"), width / 2, height / 2 + 140, 0xC0C0C0);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static Component toggleLabel() {
        return Component.translatable(ProfileManager.enabled() ? "serverbound.screen.disable" : "serverbound.screen.enable");
    }

    private static Component syncAllLabel() {
        return Component.translatable(ProfileManager.syncAllProfiles()
            ? "serverbound.screen.sync_all_disable" : "serverbound.screen.sync_all_enable");
    }
}
