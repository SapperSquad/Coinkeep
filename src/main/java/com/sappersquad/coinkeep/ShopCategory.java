package com.sappersquad.coinkeep;

public enum ShopCategory {
    FOOD("Food"),
    WEAPONS("Weapons"),
    ARMOR("Armor"),
    ENCHANTMENTS("Enchantments"),
    ORES("Ores"),
    MATERIALS("Materials"),
    RARE("Rare"),
    SIGNATURE("Signature");

    private final String label;

    ShopCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
