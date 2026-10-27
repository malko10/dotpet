package com.dotpet.pet;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Chooses once per knockdown whether to show a brief annoyed recovery reaction. */
public final class PetAngerController {
    private final RandomGenerator random;
    private boolean landing;
    private boolean selected;

    public PetAngerController() { this(ThreadLocalRandom.current()); }
    PetAngerController(RandomGenerator random) { this.random = random; }

    public boolean update(PetState state, double progress, boolean blocked) {
        if (state != PetState.LANDING) { landing = false; selected = false; return false; }
        if (!landing) { landing = true; selected = random.nextDouble() < 0.4; }
        return selected && !blocked && progress >= 0.4 && progress < 1;
    }
}
