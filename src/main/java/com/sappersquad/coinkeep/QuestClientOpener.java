package com.sappersquad.coinkeep;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class QuestClientOpener {
    public static void open() {
        Minecraft.getInstance().setScreen(new QuestScreen(Component.literal("Coinkeep")));
    }
}
