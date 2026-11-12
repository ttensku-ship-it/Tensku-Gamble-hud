package com.gamblehub;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class GambleHubClient implements ClientModInitializer {
    public static KeyMapping OPEN;

    @Override
    public void onInitializeClient() {
        Hub.load();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("gamblehub", "main"));
        OPEN = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.gamblehub.open", InputConstants.Type.KEYSYM, Keys.RIGHT_SHIFT, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) {
                if (Gui.current() == null && mc.player != null) Gui.open(new HubScreen());
            }
            Hub.tick();
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("gamblehub", "overlay"), (g, dt) -> {
            Overlay.render(g);
            PaymentAnimation.render(g);
        });

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!overlay) Chat.handle(msg.getString());
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(Chat::allowCommand);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> Hub.save());
    }
}
