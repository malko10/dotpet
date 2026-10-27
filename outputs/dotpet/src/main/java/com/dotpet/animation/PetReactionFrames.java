package com.dotpet.animation;

import com.dotpet.pet.PetState;
import java.util.List;
import java.util.Map;

/** Pixel poses derived from the active sprite, preserving custom colors and transparency. */
public final class PetReactionFrames {
    private PetReactionFrames() { }

    public static Map<PetState, Animation> create(Frame source) {
        List<Frame> wall = List.of(pose(source, 0, 0.58, 1.0, true),
                pose(source, 0, 0.74, 0.96, true), pose(source, 0, 0.9, 1.0, true), source);
        Frame lying = pose(source, -90, 1.0, 0.60, false);
        List<Frame> landing = List.of(pose(source, 0, 1.12, 0.62, false),
                pose(source, -35, 0.94, 0.8, false), lying, lying, lying,
                pose(source, -70, 0.95, 0.68, false), pose(source, -25, 0.94, 0.92, false), source);
        return Map.of(PetState.WALL_HIT, animation(wall, PetState.WALL_HIT),
                PetState.LANDING, animation(landing, PetState.LANDING));
    }

    private static Animation animation(List<Frame> frames, PetState state) {
        double fps = frames.size() * 1_000_000_000.0 / state.getReactionDurationNanos();
        return new Animation(frames, fps);
    }

    private static Frame pose(Frame source, double degrees, double widthFactor, double heightFactor, boolean wallAnchor) {
        int minX = source.getWidth(), minY = source.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getPixel(x, y) >>> 24) == 0) continue;
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX) return source;
        int sourceWidth = maxX - minX + 1, sourceHeight = maxY - minY + 1;
        double angle = Math.toRadians(degrees), cos = Math.cos(angle), sin = Math.sin(angle);
        double rotatedWidth = Math.abs(cos) * sourceWidth + Math.abs(sin) * sourceHeight;
        double rotatedHeight = Math.abs(sin) * sourceWidth + Math.abs(cos) * sourceHeight;
        int targetWidth = Math.max(1, Math.min(source.getWidth(), (int) Math.round(rotatedWidth * widthFactor)));
        int targetHeight = Math.max(1, Math.min(maxY + 1, (int) Math.round(rotatedHeight * heightFactor)));
        int left = wallAnchor ? minX : (minX + maxX + 1 - targetWidth) / 2;
        left = Math.max(0, Math.min(source.getWidth() - targetWidth, left));
        int top = maxY + 1 - targetHeight;
        int[] pixels = new int[source.getWidth() * source.getHeight()];
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                double rx = (x + 0.5) * rotatedWidth / targetWidth - rotatedWidth / 2;
                double ry = (y + 0.5) * rotatedHeight / targetHeight - rotatedHeight / 2;
                int sx = (int) Math.floor(cos * rx + sin * ry + sourceWidth / 2.0) + minX;
                int sy = (int) Math.floor(-sin * rx + cos * ry + sourceHeight / 2.0) + minY;
                if (sx >= minX && sx <= maxX && sy >= minY && sy <= maxY)
                    pixels[(top + y) * source.getWidth() + left + x] = source.getPixel(sx, sy);
            }
        }
        return new Frame(source.getWidth(), source.getHeight(), pixels);
    }
}
