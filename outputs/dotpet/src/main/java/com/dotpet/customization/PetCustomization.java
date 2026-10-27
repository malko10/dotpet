package com.dotpet.customization;

import com.dotpet.animation.Animation;
import com.dotpet.pet.PetState;
import java.util.Map;
import javafx.scene.paint.Color;

/** Selected character profile and its presentation settings. */
public record PetCustomization(String id, String name, Map<PetState, Animation> animations,
        double sizeScale, boolean horizontalFlip, Color tint) {
    public PetCustomization {
        if (id == null || id.isBlank() || name == null || name.isBlank()) throw new IllegalArgumentException("Profile needs an id and name.");
        if (!Double.isFinite(sizeScale) || sizeScale < 0.5 || sizeScale > 2.0) throw new IllegalArgumentException("Size scale must be 0.5–2.0.");
        if (animations == null || animations.get(PetState.IDLE) == null || tint == null) throw new IllegalArgumentException("Profile needs idle animation and tint.");
        animations = Map.copyOf(animations);
    }
}
