package com.gamblehub;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class Gui {
    private Gui() {}

    /** 26.2+ renamed Minecraft.setScreen to setScreenAndShow. */
    static void open(Screen s) { Minecraft.getInstance().setScreenAndShow(s); }

    /** 26.2+ moved the current screen from Minecraft.screen to Minecraft.gui.screen(). */
    static Screen current() { return Minecraft.getInstance().gui.screen(); }
}
