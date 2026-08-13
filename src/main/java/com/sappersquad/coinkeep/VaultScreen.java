package com.sappersquad.coinkeep;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.LinkedHashMap;
import java.util.Map;

public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    // Wide enough for six buttons a row; the layout is derived from
    // VaultMenu.PER_ROW so adding an amount there needs no change here.
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 150;
    private static final int HEADER_H = 24;
    private static final int BTN_W = 44;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 3;
    private static final int PAD = 10;

    private final Map<Integer, int[]> buttons = new LinkedHashMap<>();

    public VaultScreen(VaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        super.init();
        // No inventory shown, so park both vanilla labels off-screen.
        this.inventoryLabelY = this.imageHeight + 4000;
        this.titleLabelY = this.imageHeight + 4000;

        buttons.clear();
        for (int row = 0; row < 2; row++) {
            boolean depositing = row == 0;
            for (int slot = 0; slot < VaultMenu.PER_ROW; slot++) {
                buttons.put(VaultMenu.buttonId(slot, depositing), new int[]{
                        leftPos + PAD + slot * (BTN_W + BTN_GAP),
                        topPos + HEADER_H + 30 + row * 46,
                        BTN_W, BTN_H});
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Skip vanilla's blur, but still call renderBg ourselves - it is only
        // ever invoked from here (see AbstractContainerScreen).
        g.fill(0, 0, this.width, this.height, MoneyUI.BACKDROP);
        this.renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MoneyUI.panel(g, leftPos, topPos, PANEL_W, PANEL_H);
        MoneyUI.headerBar(g, leftPos, topPos, PANEL_W, HEADER_H);
        g.drawString(this.font, this.title, leftPos + PAD, topPos + 8, MoneyUI.TEXT, false);

        long stored = menu.getStored();
        Minecraft mc = Minecraft.getInstance();
        long balance = mc.player == null ? 0L : BalanceHelper.getBalance(mc.player);

        String storedText = MoneyUI.money(stored);
        g.drawString(this.font, storedText,
                leftPos + PANEL_W - PAD - this.font.width(storedText), topPos + 8, MoneyUI.GOLD, false);

        g.drawString(this.font, "Deposit  (wallet: " + MoneyUI.money(balance) + ")",
                leftPos + PAD, topPos + HEADER_H + 14, MoneyUI.TEXT_DIM, false);
        g.drawString(this.font, "Withdraw  (vaulted: " + storedText + ")",
                leftPos + PAD, topPos + HEADER_H + 60, MoneyUI.TEXT_DIM, false);

        for (Map.Entry<Integer, int[]> entry : buttons.entrySet()) {
            int id = entry.getKey();
            int[] b = entry.getValue();
            int slot = id < VaultMenu.PER_ROW ? id : id - VaultMenu.PER_ROW;
            boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            MoneyUI.tab(g, this.font, b[0], b[1], b[2], b[3], VaultMenu.labelFor(slot), false, hovered);
        }

        String note = "Vaulted money is never lost when you die.";
        g.drawString(this.font, note, leftPos + PAD, topPos + PANEL_H - 18, MoneyUI.TEXT_FAINT, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0) {
            for (Map.Entry<Integer, int[]> entry : buttons.entrySet()) {
                int[] b = entry.getValue();
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gameMode != null) {
                        mc.gameMode.handleInventoryButtonClick(menu.containerId, entry.getKey());
                        mc.getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Everything is drawn in renderBg() in absolute coordinates.
    }
}
