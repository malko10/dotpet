package com.dotpet.customization;

import com.dotpet.animation.Frame;
import java.util.Set;
import java.util.List;
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
                int center = headCenter(source, body);
                int alpha = (int) Math.round(tint.getOpacity() * 255);
                PetAccessory.headwear(accessories).stream().findFirst().ifPresent(item -> stamp(pixels, width, height,
                        center, body[1] - item.rows().size() * unit + unit, unit, alpha, item.rows()));
            }
        }
        return new Frame(width, height, pixels);
    }

    private static void stamp(int[] pixels, int width, int height, int center, int top, int unit, int alpha, List<String> rows) {
        int columns = rows.stream().mapToInt(String::length).max().orElse(0);
        int left = Math.max(0, Math.min(width - columns * unit, center - columns * unit / 2));
        top = Math.max(0, Math.min(height - rows.size() * unit, top));
        for (int y = 0; y < rows.size(); y++) for (int x = 0; x < rows.get(y).length(); x++) {
            int color = switch (rows.get(y).charAt(x)) {
                case 'K' -> 0xFF273047; case 'Y' -> 0xFFFFD15C; case 'G' -> 0xFFCA8B35; case 'H' -> 0xFFFFF1B4;
                case 'R' -> 0xFFED788E; case 'r' -> 0xFFA84D6A; case 'C' -> 0xFF72DFF0;
                case 'B' -> 0xFF526994; case 'b' -> 0xFF344364; case 'P' -> 0xFF9C83D8; case 'p' -> 0xFF68519D;
                case 'A' -> 0xFFD4C6F4; case 'W' -> 0xFFF6F3E9; case 'T' -> 0xFFDFB477; case 't' -> 0xFFAE8248;
                case 'N' -> 0xFF6EAF78; case 'n' -> 0xFF397C5D; default -> 0;
            };
            if (color == 0) continue;
            color = (alpha << 24) | (color & 0xFFFFFF);
            for (int dy = 0; dy < unit; dy++) for (int dx = 0; dx < unit; dx++) {
                int px = left + x * unit + dx, py = top + y * unit + dy;
                if (px >= 0 && px < width && py >= 0 && py < height) pixels[py * width + px] = color;
            }
        }
    }

    /** Follow the dome rather than the tail's changing width during a run. */
    private static int headCenter(Frame frame, int[] body) {
        int left = body[2], right = body[0];
        int band = Math.max(1, (body[3] - body[1] + 1) / 4);
        for (int y = body[1]; y < body[1] + band; y++) for (int x = body[0]; x <= body[2]; x++) {
            if ((frame.getPixel(x, y) >>> 24) != 0) { left = Math.min(left, x); right = Math.max(right, x); }
        }
        return (left + right + 1) / 2;
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
