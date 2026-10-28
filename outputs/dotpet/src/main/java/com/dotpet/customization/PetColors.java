package com.dotpet.customization;

import javafx.scene.paint.Color;

/** Shared preview/live coloring, retaining the slime's eyes, highlights and pixel shading. */
public final class PetColors {
    private static final Color BASE_BLUE = Color.web("#75B9FF");
    private PetColors() { }

    public static Color apply(int argb, Color tint, boolean recolorBody) {
        double alpha = ((argb >>> 24) & 255) / 255.0 * tint.getOpacity();
        int red = (argb >>> 16) & 255, green = (argb >>> 8) & 255, blue = argb & 255;
        if (!recolorBody) return Color.rgb((int) Math.round(red * tint.getRed()),
                (int) Math.round(green * tint.getGreen()), (int) Math.round(blue * tint.getBlue()), alpha);
        Color source = Color.rgb(red, green, blue);
        if (source.getHue() < 175 || source.getHue() > 255 || source.getSaturation() < 0.16 || source.getBrightness() < 0.2)
            return Color.color(source.getRed(), source.getGreen(), source.getBlue(), alpha);
        return Color.hsb(tint.getHue() + source.getHue() - BASE_BLUE.getHue(),
                Math.min(1, source.getSaturation() * tint.getSaturation() / BASE_BLUE.getSaturation()),
                source.getBrightness() * tint.getBrightness(), alpha);
    }
}
