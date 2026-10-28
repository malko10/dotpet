package com.dotpet.pet;

import com.dotpet.animation.Animation;
import com.dotpet.animation.DefaultPetAnimations;
import com.dotpet.animation.Frame;
import com.dotpet.animation.PetReactionFrames;
import com.dotpet.customization.PetAppearance;
import com.dotpet.customization.PetAccessory;
import java.util.Set;
import java.util.IdentityHashMap;
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
    private boolean recolorBody;
    private Set<PetAccessory> accessories = Set.of();
    private final Map<Frame, Frame> appearanceCache = new IdentityHashMap<>();
    private Map<PetState, Animation> appearanceReactions;
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
        invalidateAppearance();
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
        invalidateAppearance();
    }

    public void setHorizontalFlip(boolean horizontalFlip) {
        this.horizontalFlip = horizontalFlip;
    }

    public void setRecolorBody(boolean recolorBody) { this.recolorBody = recolorBody; invalidateAppearance(); }
    public void setAccessories(Set<PetAccessory> accessories) { this.accessories = Set.copyOf(accessories); invalidateAppearance(); }
    private void invalidateAppearance() { appearanceCache.clear(); appearanceReactions = null; }
    private Frame appearance(Frame frame) {
        return appearanceCache.computeIfAbsent(frame, source -> PetAppearance.compose(source, tintColor, recolorBody, accessories));
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
        Frame frame;
        if (pet.getState().isReaction()) {
            if (appearanceReactions == null) appearanceReactions = PetReactionFrames.create(appearance(animations.get(PetState.IDLE).getFrame(0)));
            frame = appearanceReactions.get(pet.getState()).frameAtClamped(elapsed);
        } else frame = appearance(animation.frameAt(elapsed));
        graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double output = canvas.getScene() != null && canvas.getScene().getWindow() != null
                ? Math.min(canvas.getScene().getWindow().getRenderScaleX(), canvas.getScene().getWindow().getRenderScaleY()) : 1;
        var layout = PixelSizing.fit(frame.getWidth(), frame.getHeight(), canvas.getWidth(), canvas.getHeight(), output);
        double pixelScale = layout.unit(), offsetX = layout.x(), offsetY = layout.y();
        double mirrorWidth = Math.round(canvas.getWidth() * output) / output;

        graphics.save();
        // The author draws a left-facing original; rightward motion uses the reflected image.
        if (pet.isFacingRight() ^ horizontalFlip) {
            graphics.translate(mirrorWidth, 0);
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
                graphics.setFill(Color.rgb((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, (argb >>> 24) / 255.0));
                double left = x * pixelScale, top = y * pixelScale;
                double right = (x + 1) * pixelScale, bottom = (y + 1) * pixelScale;
                graphics.fillRect(left, top, right - left, bottom - top);
            }
        }
        graphics.restore();
        if (maxX < minX || tintColor.getOpacity() == 0) spriteBounds = Rectangle2D.EMPTY;
        else {
            double left = minX * pixelScale, right = (maxX + 1) * pixelScale;
            double top = minY * pixelScale, bottom = (maxY + 1) * pixelScale;
            double x = pet.isFacingRight() ^ horizontalFlip ? mirrorWidth - offsetX - right : offsetX + left;
            spriteBounds = new Rectangle2D(x, offsetY + top, right - left, bottom - top);
        }
    }

}
