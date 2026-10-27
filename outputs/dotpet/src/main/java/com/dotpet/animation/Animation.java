package com.dotpet.animation;

import java.util.List;

/** Immutable looping animation made from same-sized pixel frames. */
public final class Animation {
    private final List<Frame> frames;
    private final double framesPerSecond;

    public Animation(List<Frame> frames, double framesPerSecond) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("An animation needs at least one frame.");
        }
        if (!Double.isFinite(framesPerSecond) || framesPerSecond <= 0) {
            throw new IllegalArgumentException("Animation speed must be positive and finite.");
        }
        Frame first = frames.get(0);
        if (frames.stream().anyMatch(frame -> frame.getWidth() != first.getWidth()
                || frame.getHeight() != first.getHeight())) {
            throw new IllegalArgumentException("Animation frames must all use the same dimensions.");
        }
        this.frames = List.copyOf(frames);
        this.framesPerSecond = framesPerSecond;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public double getFramesPerSecond() {
        return framesPerSecond;
    }

    public Frame getFrame(int index) {
        return frames.get(index);
    }

    public Frame frameAt(long elapsedNanos) {
        long elapsed = Math.max(0, elapsedNanos);
        long frameIndex = (long) (elapsed / 1_000_000_000.0 * framesPerSecond) % frames.size();
        return frames.get((int) frameIndex);
    }

    /** A reaction holds its last frame instead of looping before its behavior state ends. */
    public Frame frameAtClamped(long elapsedNanos) {
        long index = (long) (Math.max(0, elapsedNanos) / 1_000_000_000.0 * framesPerSecond);
        return frames.get((int) Math.min(frames.size() - 1L, index));
    }
}
