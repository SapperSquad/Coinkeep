package com.sappersquad.coinkeep;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared look-and-feel for the Ledger and Vault screens so they read
 * as the same mod. Everything here is drawn with plain fills - no texture
 * atlas and no extra assets to ship.
 */
public final class MoneyUI {

    private MoneyUI() {
    }

    /** Translucent so the world still reads behind the panel. */
    public static final int BACKDROP = 0xB40E0F12;

    public static final int PANEL_BG = 0xFF23252B;
    public static final int PANEL_BORDER = 0xFF14161A;
    public static final int PANEL_EDGE = 0xFF3A3F49;
    public static final int HEADER_BG = 0xFF2C3038;
    public static final int DIVIDER = 0xFF3A3F49;

    public static final int ROW_HOVER = 0xFF31363F;

    public static final int TEXT = 0xFFE8EAED;
    public static final int TEXT_DIM = 0xFF9AA0A6;
    public static final int TEXT_FAINT = 0xFF6E747C;

    public static final int GOLD = 0xFFFFC94A;
    public static final int GREEN = 0xFF5FD08A;
    public static final int RED = 0xFFE06C6C;

    public static final int TAB_ACTIVE = 0xFF23252B;
    public static final int TAB_HOVER = 0xFF262A31;
    public static final int TAB_IDLE = 0xFF1A1C21;

    public static final int SCROLL_TRACK = 0xFF1A1C21;
    public static final int SCROLL_THUMB = 0xFF4A505B;

    public static String money(long value) {
        return "$" + String.format("%,d", value);
    }

    /** Panel with a dark outline plus a lighter top/left edge for depth. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER);
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
        g.fill(x, y, x + 1, y + h, PANEL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_BORDER);
    }

    public static void headerBar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h, HEADER_BG);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, DIVIDER);
    }

    /** A tab whose active state is marked by a gold underline. */
    public static void tab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
                           String label, boolean active, boolean hovered) {
        int bg = active ? TAB_ACTIVE : (hovered ? TAB_HOVER : TAB_IDLE);
        g.fill(x, y, x + w, y + h, bg);
        if (active) {
            g.fill(x, y + h - 2, x + w, y + h, GOLD);
        }
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2,
                active ? TEXT : TEXT_DIM, false);
    }

    public static void progressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float pct, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF15171B);
        int filled = Math.max(0, Math.min(w, Math.round(w * pct)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, fill);
        }
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int contentH, int viewH, int scroll) {
        if (contentH <= viewH) {
            return;
        }
        g.fill(x, y, x + 4, y + h, SCROLL_TRACK);
        int thumbH = Math.max(16, (int) ((long) viewH * h / contentH));
        int maxScroll = contentH - viewH;
        int thumbY = y + (maxScroll <= 0 ? 0 : (h - thumbH) * scroll / maxScroll);
        g.fill(x, thumbY, x + 4, thumbY + thumbH, SCROLL_THUMB);
    }

    /** Truncates with an ellipsis so a long name can never run into the price column. */
    public static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
}
