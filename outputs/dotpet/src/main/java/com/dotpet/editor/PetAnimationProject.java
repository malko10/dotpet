package com.dotpet.editor;

import com.dotpet.animation.Animation;
import com.dotpet.animation.Frame;
import com.dotpet.pet.PetState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Editable animation timelines for the four pet behaviors. */
public final class PetAnimationProject {
    private static final Map<PetState, Integer> DEFAULT_FRAME_COUNTS = Map.of(
            PetState.IDLE, 2, PetState.WALKING, 4, PetState.RUNNING, 6, PetState.SLEEPING, 2);

    private final EnumMap<PetState, List<Frame>> animations = new EnumMap<>(PetState.class);
    private final EnumMap<PetState, Double> frameRates = new EnumMap<>(PetState.class);
    private final int canvasSize;
    private PetState selectedState = PetState.IDLE;
    private int selectedFrame;

    public PetAnimationProject(Frame initialFrame) {
        this(initialFrame, Map.of());
    }

    public PetAnimationProject(Frame initialFrame, Map<PetState, Animation> seedAnimations) {
        if (initialFrame.getWidth() != initialFrame.getHeight()) {
            throw new IllegalArgumentException("Animation frames must be square.");
        }
        canvasSize = initialFrame.getWidth();
        for (PetState state : editableStates()) {
            ArrayList<Frame> frames = new ArrayList<>();
            Animation seed = seedAnimations.get(state);
            if (seed == null) {
                for (int i = 0; i < DEFAULT_FRAME_COUNTS.get(state); i++) frames.add(initialFrame);
                frameRates.put(state, 8.0);
            } else {
                for (int i = 0; i < seed.getFrameCount(); i++) frames.add(scaleNearest(seed.getFrame(i), canvasSize));
                frameRates.put(state, seed.getFramesPerSecond());
            }
            animations.put(state, frames);
        }
    }

    private PetAnimationProject(int canvasSize, PetState selectedState,
            int selectedFrame, EnumMap<PetState, List<Frame>> animations) {
        this.canvasSize = canvasSize;
        this.selectedState = selectedState;
        this.selectedFrame = selectedFrame;
        animations.forEach((state, frames) -> this.animations.put(state, new ArrayList<>(frames)));
    }

    public int getCanvasSize() { return canvasSize; }
    public double getFramesPerSecond() { return frameRates.get(selectedState); }
    public double getFramesPerSecond(PetState state) { return frameRates.get(state); }
    public void setFramesPerSecond(double fps) {
        if (!Double.isFinite(fps) || fps < 1 || fps > 24) throw new IllegalArgumentException("FPS must be 1–24.");
        frameRates.put(selectedState, fps);
    }
    public void setFramesPerSecond(PetState state, double fps) {
        if (!frameRates.containsKey(state) || !Double.isFinite(fps) || fps < 1 || fps > 24)
            throw new IllegalArgumentException("FPS must be 1–24 for an editable animation.");
        frameRates.put(state, fps);
    }
    public PetState getSelectedState() { return selectedState; }
    public int getSelectedFrameIndex() { return selectedFrame; }
    public List<PetState> getEditableStates() { return editableStates(); }
    public int getFrameCount(PetState state) { return animations.get(state).size(); }
    public Frame getFrame(PetState state, int index) { return animations.get(state).get(index); }

    public void select(PetState state, int frameIndex) {
        List<Frame> frames = requireEditableState(state);
        if (frameIndex < 0 || frameIndex >= frames.size()) throw new IndexOutOfBoundsException("Frame index out of range.");
        selectedState = state;
        selectedFrame = frameIndex;
    }

    public void updateSelected(Frame frame) {
        validateFrame(frame);
        animations.get(selectedState).set(selectedFrame, frame);
    }

    public void replaceFrames(PetState state, List<Frame> frames) {
        if (!animations.containsKey(state) || frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Animation must contain at least one editable frame.");
        }
        for (Frame frame : frames) validateFrame(frame);
        animations.put(state, new ArrayList<>(frames));
        if (state == selectedState) selectedFrame = Math.min(selectedFrame, frames.size() - 1);
    }

    public void addFrame() {
        List<Frame> frames = animations.get(selectedState);
        frames.add(selectedFrame + 1, frames.get(selectedFrame));
        selectedFrame++;
    }

    public void copyFrame() { addFrame(); }

    /** Uses the current drawing in every existing frame while preserving timeline lengths and FPS. */
    public void copySelectedFrameToAll() {
        Frame source = getFrame(selectedState, selectedFrame);
        for (PetState state : editableStates()) {
            List<Frame> frames = animations.get(state);
            for (int index = 0; index < frames.size(); index++) frames.set(index, source);
        }
    }

    public PetAnimationProject copy() {
        PetAnimationProject copy = new PetAnimationProject(canvasSize, selectedState, selectedFrame, copyAnimations());
        copy.frameRates.putAll(frameRates);
        return copy;
    }

    public boolean isEmpty() {
        for (List<Frame> frames : animations.values()) {
            for (Frame frame : frames) {
                for (int argb : frame.copyPixels()) if ((argb >>> 24) != 0) return false;
            }
        }
        return true;
    }

    public void deleteFrame() {
        List<Frame> frames = animations.get(selectedState);
        if (frames.size() <= 1) throw new IllegalStateException("At least one frame is required.");
        frames.remove(selectedFrame);
        selectedFrame = Math.min(selectedFrame, frames.size() - 1);
    }

    public void moveSelectedFrame(int offset) {
        List<Frame> frames = animations.get(selectedState);
        int target = selectedFrame + offset;
        if (target < 0 || target >= frames.size()) return;
        Frame current = frames.set(selectedFrame, frames.get(target));
        frames.set(target, current);
        selectedFrame = target;
    }

    public PetAnimationProject resizedCopy(int newSize) {
        if (newSize != 16 && newSize != 32 && newSize != 64) {
            throw new IllegalArgumentException("Canvas must be 16, 32, or 64 pixels square.");
        }
        EnumMap<PetState, List<Frame>> scaled = new EnumMap<>(PetState.class);
        for (PetState state : editableStates()) {
            scaled.put(state, animations.get(state).stream().map(frame -> scaleNearest(frame, newSize)).toList());
        }
        PetAnimationProject copy = new PetAnimationProject(newSize, selectedState, selectedFrame, scaled);
        copy.frameRates.putAll(frameRates);
        return copy;
    }

    public Map<PetState, Animation> toAnimations() {
        EnumMap<PetState, Animation> result = new EnumMap<>(PetState.class);
        for (PetState state : editableStates()) result.put(state, new Animation(animations.get(state), frameRates.get(state)));
        return Map.copyOf(result);
    }

    public EnumMap<PetState, List<Frame>> copyAnimations() {
        EnumMap<PetState, List<Frame>> result = new EnumMap<>(PetState.class);
        for (PetState state : editableStates()) result.put(state, List.copyOf(animations.get(state)));
        return result;
    }

    private List<Frame> requireEditableState(PetState state) {
        List<Frame> frames = animations.get(state);
        if (frames == null) throw new IllegalArgumentException("This pet state has no editable animation.");
        return frames;
    }

    private void validateFrame(Frame frame) {
        if (frame.getWidth() != canvasSize || frame.getHeight() != canvasSize) {
            throw new IllegalArgumentException("Frame size must match the animation canvas.");
        }
    }

    private static Frame scaleNearest(Frame source, int size) {
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = source.getPixel(x * source.getWidth() / size, y * source.getHeight() / size);
            }
        }
        return new Frame(size, size, pixels);
    }

    private static List<PetState> editableStates() {
        return List.of(PetState.IDLE, PetState.WALKING, PetState.RUNNING, PetState.SLEEPING);
    }
}
