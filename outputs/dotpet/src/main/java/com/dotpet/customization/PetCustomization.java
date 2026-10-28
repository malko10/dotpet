package com.dotpet.customization;

import com.dotpet.animation.Animation;
import com.dotpet.pet.PetState;
import java.util.Map;
import javafx.scene.paint.Color;

/** Selected character profile and its presentation settings. */
public record PetCustomization(String id, String name, Map<PetState, Animation> animations,
        double sizeScale, boolean horizontalFlip, Color tint, boolean recolorBody) {
    public PetCustomization(String id, String name, Map<PetState, Animation> animations,
            double sizeScale, boolean horizontalFlip, Color tint) {
        this(id, name, animations, sizeScale, horizontalFlip, tint, false);
    }
    public static PetCustomization builtIn(Map<PetState, Animation> animations, double sizeScale,
            boolean horizontalFlip, Color tint) {
        return new PetCustomization("builtin", "기본 슬라임", animations, sizeScale, horizontalFlip, tint);
    }

    /** Refresh bundled artwork after an update while retaining the user's appearance preferences. */
    public PetCustomization resolveBuiltIn(Map<PetState, Animation> currentAnimations) {
        return id.equals("builtin") ? new PetCustomization("builtin", "기본 슬라임", currentAnimations,
                sizeScale, horizontalFlip, tint, recolorBody) : this;
    }

    public PetCustomization {
        if (id == null || id.isBlank() || name == null || name.isBlank()) throw new IllegalArgumentException("Profile needs an id and name.");
        if (!Double.isFinite(sizeScale) || sizeScale < 0.5 || sizeScale > 2.0) throw new IllegalArgumentException("Size scale must be 0.5–2.0.");
        if (animations == null || animations.get(PetState.IDLE) == null || tint == null) throw new IllegalArgumentException("Profile needs idle animation and tint.");
        animations = Map.copyOf(animations);
    }
}
