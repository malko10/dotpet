package com.dotpet.editor;

import com.dotpet.animation.Frame;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** Editable ARGB pixel document with bounded snapshot-based undo and redo. */
public final class PixelDocument {
    private static final int HISTORY_LIMIT = 80;

    private int width;
    private int height;
    private int[] pixels;
    private final Deque<int[]> undo = new ArrayDeque<>();
    private final Deque<int[]> redo = new ArrayDeque<>();

    public PixelDocument(int size) {
        resize(size);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getPixel(int x, int y) { return pixels[index(x, y)]; }
    public int[] copyPixels() { return Arrays.copyOf(pixels, pixels.length); }

    public void beginEdit() {
        undo.addLast(copyPixels());
        while (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        redo.clear();
    }

    public void paint(int x, int y, int argb) {
        if (inside(x, y)) pixels[y * width + x] = argb;
    }

    public void clear() {
        beginEdit();
        Arrays.fill(pixels, 0);
    }

    public void resize(int size) {
        if (size != 16 && size != 32 && size != 64) {
            throw new IllegalArgumentException("Canvas must be 16, 32, or 64 pixels square.");
        }
        width = size;
        height = size;
        pixels = new int[size * size];
        undo.clear();
        redo.clear();
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.addLast(copyPixels());
        pixels = undo.removeLast();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.addLast(copyPixels());
        pixels = redo.removeLast();
        return true;
    }

    public Frame toFrame() { return new Frame(width, height, pixels); }

    public void replace(int newWidth, int newHeight, int[] newPixels) {
        if ((newWidth != 16 && newWidth != 32 && newWidth != 64)
                || newWidth != newHeight || newPixels == null || newPixels.length != newWidth * newHeight) {
            throw new IllegalArgumentException("Unsupported or invalid pixel project.");
        }
        width = newWidth;
        height = newHeight;
        pixels = Arrays.copyOf(newPixels, newPixels.length);
        undo.clear();
        redo.clear();
    }

    private boolean inside(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }

    private int index(int x, int y) {
        if (!inside(x, y)) throw new IndexOutOfBoundsException("Pixel outside canvas.");
        return y * width + x;
    }
}
