package com.dotpet.pet;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Times occasional chatter separately from movement and JavaFX window rendering. */
public final class PetSpeechController {
    private static final long SECOND = 1_000_000_000L;
    private static final long VISIBLE_NANOS = 7 * SECOND;
    private final RandomGenerator random;
    private long nextAt;
    private long hideAt;
    private int previousIndex = -1;
    private String current;
    private boolean wasBlocked;

    public PetSpeechController(long now) { this(now, ThreadLocalRandom.current()); }

    PetSpeechController(long now, RandomGenerator random) {
        this.random = random;
        nextAt = now + random.nextLong(12, 21) * SECOND;
    }

    /** Returns the active line, or null while quiet. Call on the application's UI thread. */
    public String update(long now, boolean blocked) {
        if (blocked) {
            current = null;
            wasBlocked = true;
            return null;
        }
        if (wasBlocked) {
            wasBlocked = false;
            nextAt = now + random.nextLong(12, 21) * SECOND;
        }
        if (current != null && now >= hideAt) {
            current = null;
            nextAt = now + random.nextLong(35, 76) * SECOND;
        } else if (current == null && now >= nextAt) {
            int count = PetRemarks.LINES.size();
            int choice = random.nextInt(previousIndex < 0 ? count : count - 1);
            if (previousIndex >= 0 && choice >= previousIndex) choice++;
            previousIndex = choice;
            current = PetRemarks.LINES.get(choice);
            hideAt = now + VISIBLE_NANOS;
        }
        return current;
    }
}
