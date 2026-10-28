package com.dotpet.pet;

import com.dotpet.animation.Animation;
import com.dotpet.animation.DefaultPetAnimations;
import com.dotpet.animation.Frame;
import com.dotpet.animation.PetReactionFrames;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Renders state-specific animation frames as crisp pixels on a transparent canvas. */
public final class PetRenderer {
    private final Canvas canvas;
    private final GraphicsContext graphics;
    private final Map<PetState, Animation> animations;
    private final Map<PetState, Animation> builtInAnimations;
    private Color tintColor = Color.WHITE;
    private boolean horizontalFlip;
    private PetState renderedState;
    private long stateStartedAt;
    private Rectangle2D spriteBounds = Rectangle2D.EMPTY;

    public PetRenderer(Canvas canvas) {
        this.canvas = canvas;
        this.graphics = canvas.getGraphicsContext2D();
        this.graphics.setImageSmoothing(false);
        this.builtInAnimations = DefaultPetAnimations.get();
        this.animations = new EnumMap<>(builtInAnimations);
    }

    /** Applies edited behavior animations to the live desktop pet. */
    public void applyUserAnimations(Map<PetState, Animation> editedAnimations) {
        for (PetState state : List.of(PetState.IDLE, PetState.WALKING, PetState.RUNNING, PetState.SLEEPING)) {
            Animation animation = editedAnimations.get(state);
            if (animation != null) animations.put(state, animation);
        }
        Animation editedIdle = editedAnimations.get(PetState.IDLE);
        if (editedIdle != null) {
            animations.put(PetState.FALLING, editedIdle);
            animations.putAll(PetReactionFrames.create(editedIdle.getFrame(0)));
        }
        renderedState = null;
    }

    /** Creates a 32×32 editable starter image by nearest-neighbor scaling the built-in idle sprite. */
    public Frame createEditableStarterFrame() {
        Frame source = animations.get(PetState.IDLE).getFrame(0);
        int size = 32;
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = source.getPixel(x * source.getWidth() / size, y * source.getHeight() / size);
            }
        }
        return new Frame(size, size, pixels);
    }

    /** Returns editable-size copies of the current behavior animations. */
    public Map<PetState, Animation> createEditableAnimations() {
        return editableCopies(animations);
    }

    public Map<PetState, Animation> createBuiltInEditableAnimations() {
        return editableCopies(builtInAnimations);
    }

    public void setTintColor(Color tintColor) {
        this.tintColor = tintColor == null ? Color.WHITE : tintColor;
    }

    public void setHorizontalFlip(boolean horizontalFlip) {
        this.horizontalFlip = horizontalFlip;
    }

    /** Actual visible pixels in canvas coordinates, including custom artwork and reflection. */
    public Rectangle2D getSpriteBounds() { return spriteBounds; }

    private static Map<PetState, Animation> editableCopies(Map<PetState, Animation> sourceAnimations) {
        EnumMap<PetState, Animation> result = new EnumMap<>(PetState.class);
        for (PetState state : List.of(PetState.IDLE, PetState.WALKING, PetState.RUNNING, PetState.SLEEPING)) {
            Animation source = sourceAnimations.get(state);
            List<Frame> frames = new ArrayList<>();
            for (int i = 0; i < source.getFrameCount(); i++) {
                Frame original = source.getFrame(i);
                int[] pixels = new int[32 * 32];
                for (int y = 0; y < 32; y++) {
                    for (int x = 0; x < 32; x++) {
                        pixels[y * 32 + x] = original.getPixel(x * original.getWidth() / 32,
                                y * original.getHeight() / 32);
                    }
                }
                frames.add(new Frame(32, 32, pixels));
            }
            result.put(state, new Animation(frames, source.getFramesPerSecond()));
        }
        return Map.copyOf(result);
    }

    public void render(Pet pet, long now) {
        if (pet.getState() != renderedState) {
            renderedState = pet.getState();
            stateStartedAt = now;
        }

        // Falling has no separate editor timeline: use the current appearance's idle frames.
        PetState animationState = pet.getState() == PetState.FALLING ? PetState.IDLE : pet.getState();
        Animation animation = animations.get(animationState);
        long elapsed = Math.max(0, now - stateStartedAt);
        Frame frame = pet.getState().isReaction() ? animation.frameAtClamped(elapsed) : animation.frameAt(elapsed);
        graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double pixelScale = Math.min(canvas.getWidth() / frame.getWidth(), canvas.getHeight() / frame.getHeight());
        double drawWidth = frame.getWidth() * pixelScale;
        double drawHeight = frame.getHeight() * pixelScale;
        double offsetX = (canvas.getWidth() - drawWidth) / 2;
        double offsetY = (canvas.getHeight() - drawHeight) / 2;

        graphics.save();
        // The author draws a left-facing original; rightward motion uses the reflected image.
        if (pet.isFacingRight() ^ horizontalFlip) {
            graphics.translate(canvas.getWidth(), 0);
            graphics.scale(-1, 1);
        }
        graphics.translate(offsetX, offsetY);

        int minX = frame.getWidth(), minY = frame.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int argb = frame.getPixel(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                graphics.setFill(toColor(argb));
                double left = Math.round(x * pixelScale);
                double top = Math.round(y * pixelScale);
                double right = Math.round((x + 1) * pixelScale);
                double bottom = Math.round((y + 1) * pixelScale);
                graphics.fillRect(left, top, right - left, bottom - top);
            }
        }
        graphics.restore();
        if (maxX < minX || tintColor.getOpacity() == 0) spriteBounds = Rectangle2D.EMPTY;
        else {
            double left = Math.round(minX * pixelScale), right = Math.round((maxX + 1) * pixelScale);
            double top = Math.round(minY * pixelScale), bottom = Math.round((maxY + 1) * pixelScale);
            double x = pet.isFacingRight() ^ horizontalFlip ? canvas.getWidth() - offsetX - right : offsetX + left;
            spriteBounds = new Rectangle2D(x, offsetY + top, right - left, bottom - top);
        }
    }

    private Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (int) Math.round(((argb >>> 16) & 0xFF) * tintColor.getRed());
        int green = (int) Math.round(((argb >>> 8) & 0xFF) * tintColor.getGreen());
        int blue = (int) Math.round((argb & 0xFF) * tintColor.getBlue());
        return Color.rgb(red, green, blue, alpha / 255.0 * tintColor.getOpacity());
    }
}
