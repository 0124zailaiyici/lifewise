package com.lifewise.common;

public enum Scene {
    cooking("🍳", "做饭助手"),
    shopping("🛒", "买菜指南"),
    repair("🔧", "修理指南"),
    housework("🏠", "家务技巧"),
    health("🏥", "健康常识"),
    fashion("👔", "穿搭指南"),
    etiquette("🤝", "社交礼仪"),
    pet("🐾", "宠物照顾"),
    writing("✍️", "写作助手"),
    mealplan("📅", "食谱推荐"),
    other("📝", "其他");

    public final String icon;
    public final String label;

    Scene(String icon, String label) {
        this.icon = icon;
        this.label = label;
    }

    public static Scene fromString(String s) {
        for (Scene scene : values()) {
            if (scene.name().equalsIgnoreCase(s)) return scene;
        }
        return other;
    }
}
