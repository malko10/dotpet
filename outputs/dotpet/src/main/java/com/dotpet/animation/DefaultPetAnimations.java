package com.dotpet.animation;

import com.dotpet.pet.PetState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** The bundled 32×32 slime artwork and its left-facing behavior timelines. */
public final class DefaultPetAnimations {
    private DefaultPetAnimations() { }

    public static Map<PetState, Animation> get() { return Holder.ANIMATIONS; }

    private static final class Holder {
        private static final Map<PetState, Animation> ANIMATIONS = load();
    }

    private static Map<PetState, Animation> load() {
        EnumMap<PetState, Animation> animations = new EnumMap<>(PetState.class);
        animations.put(PetState.IDLE, readAnimation("idle", 2, 2));
        animations.put(PetState.WALKING, readAnimation("walk_left", 4, 7));
        animations.put(PetState.RUNNING, readAnimation("run_left", 6, 11));
        animations.put(PetState.SLEEPING, readAnimation("sleep", 2, 2));
        animations.put(PetState.FALLING, animations.get(PetState.IDLE));
        animations.putAll(PetReactionFrames.create(animations.get(PetState.IDLE).getFrame(0)));
        return Map.copyOf(animations);
    }

    private static Animation readAnimation(String prefix, int count, double fps) {
        var frames = new ArrayList<Frame>(count);
        for (int number = 1; number <= count; number++) {
            String resource = String.format(Locale.ROOT, "/images/slime/%s_%02d.png", prefix, number);
            try (var stream = DefaultPetAnimations.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing bundled pet image: " + resource);
                var image = ImageIO.read(stream);
                if (image == null || image.getWidth() != 32 || image.getHeight() != 32)
                    throw new IOException("Bundled pet image must be a 32×32 PNG: " + resource);
                frames.add(new Frame(32, 32, image.getRGB(0, 0, 32, 32, null, 0, 32)));
            } catch (IOException failure) {
                throw new IllegalStateException("기본 슬라임 이미지를 읽을 수 없습니다: " + resource, failure);
            }
        }
        return new Animation(frames, fps);
    }
}
