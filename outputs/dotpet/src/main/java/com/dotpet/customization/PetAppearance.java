package com.dotpet.customization;

import com.dotpet.animation.Frame;
import java.util.Set;
import javafx.scene.paint.Color;

/** Composes appearance layers without modifying the original editor pixels. */
public final class PetAppearance {
    private PetAppearance() { }

    public static Frame compose(Frame source, Color tint, boolean recolor, Set<PetAccessory> accessories) {
        if (tint.equals(Color.WHITE) && !recolor && accessories.isEmpty()) return source;
        int width = source.getWidth(), height = source.getHeight();
        int[] pixels = source.copyPixels();
        for (int i = 0; i < pixels.length; i++) {
            Color color = PetColors.apply(pixels[i], tint, recolor);
            pixels[i] = ((int) Math.round(color.getOpacity() * 255) << 24)
                    | ((int) Math.round(color.getRed() * 255) << 16)
                    | ((int) Math.round(color.getGreen() * 255) << 8) | (int) Math.round(color.getBlue() * 255);
        }
        if (!accessories.isEmpty()) {
            int[] body = bodyBounds(source);
            if (body != null) {
                int unit = Math.max(1, (body[2] - body[0] + 1) / 28);
                int center = (body[0] + body[2] + 1) / 2;
                int alpha = (int) Math.round(tint.getOpacity() * 255);
                if (accessories.contains(PetAccessory.CROWN)) stamp(pixels, width, height, center, body[1] - 6 * unit + 1, unit, alpha,
                        new String[]{"Y.....Y.....Y", "YY...YYY...YY", "YKY.YYRY.YYKY", "YYYYYYYYYYYYY", ".YYYYYYYYYYY.", ".KKKKKKKKKKK."});
                else if (accessories.contains(PetAccessory.HAT)) stamp(pixels, width, height, center, body[1] - 7 * unit + 1, unit, alpha,
                        new String[]{"...KKKKKKK...", "...KBBBBBK...", "...KBBBBBK...", "...KBBBBBK...", "...KRRRRRK...", ".KKKKKKKKKKK.", "KKKKKKKKKKKKK"});
                int faceX = body[0] + (int) Math.round((body[2] - body[0] + 1) * .32);
                int faceY = body[1] + (int) Math.round((body[3] - body[1] + 1) * .38);
                if (accessories.contains(PetAccessory.EYE_PATCH)) stamp(pixels, width, height, faceX - 2 * unit, faceY - unit, unit, alpha,
                        new String[]{"KKKKKKKKK", ".KKKKK...", ".KKKKK...", "..KKK...."});
                if (accessories.contains(PetAccessory.MUSTACHE)) stamp(pixels, width, height, faceX + unit,
                        body[1] + (int) Math.round((body[3] - body[1] + 1) * .65), unit, alpha,
                        new String[]{".DD...DD.", "DDDD.DDDD", ".DDDDDDD."});
            }
        }
        return new Frame(width, height, pixels);
    }

    private static void stamp(int[] pixels, int width, int height, int center, int top, int unit, int alpha, String[] rows) {
        int columns = java.util.Arrays.stream(rows).mapToInt(String::length).max().orElse(0);
        int left = Math.max(0, Math.min(width - columns * unit, center - columns * unit / 2));
        top = Math.max(0, Math.min(height - rows.length * unit, top));
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
            int color = switch (rows[y].charAt(x)) {
                case 'K' -> 0xFF202333; case 'Y' -> 0xFFFFD15C; case 'R' -> 0xFFE97089;
                case 'B' -> 0xFF53658D; case 'D' -> 0xFF563D39; default -> 0;
            };
            if (color == 0) continue;
            color = (alpha << 24) | (color & 0xFFFFFF);
            for (int dy = 0; dy < unit; dy++) for (int dx = 0; dx < unit; dx++) {
                int px = left + x * unit + dx, py = top + y * unit + dy;
                if (px >= 0 && px < width && py >= 0 && py < height) pixels[py * width + px] = color;
            }
        }
    }

    /** Largest connected body ignores detached sleep letters and running dust. */
    private static int[] bodyBounds(Frame frame) {
        int w = frame.getWidth(), h = frame.getHeight(), largest = 0;
        boolean[] visited = new boolean[w * h]; int[] queue = new int[w * h]; int[] best = null;
        for (int start = 0; start < visited.length; start++) {
            if (visited[start] || (frame.getPixel(start % w, start / w) >>> 24) == 0) continue;
            int first = 0, end = 0, left = w, top = h, right = -1, bottom = -1;
            queue[end++] = start; visited[start] = true;
            while (first < end) {
                int p = queue[first++], x = p % w, y = p / w;
                left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
                int[] neighbors = {x > 0 ? p - 1 : -1, x + 1 < w ? p + 1 : -1, y > 0 ? p - w : -1, y + 1 < h ? p + w : -1};
                for (int n : neighbors) if (n >= 0 && !visited[n] && (frame.getPixel(n % w, n / w) >>> 24) != 0) {
                    visited[n] = true; queue[end++] = n;
                }
            }
            if (end > largest) { largest = end; best = new int[]{left, top, right, bottom}; }
        }
        return best;
    }
}
