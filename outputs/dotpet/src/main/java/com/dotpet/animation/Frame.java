package com.dotpet.animation;

import java.util.Arrays;

/** Immutable, single-frame ARGB pixel image. A zero pixel is transparent. */
public final class Frame {
    private final int width;
    private final int height;
    private final int[] pixels;

    public Frame(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0 || pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException("Frame dimensions must match the pixel array.");
        }
        this.width = width;
        this.height = height;
        this.pixels = Arrays.copyOf(pixels, pixels.length);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Pixel coordinate is outside the frame.");
        }
        return pixels[y * width + x];
    }

    public int[] copyPixels() {
        return Arrays.copyOf(pixels, pixels.length);
    }
}
