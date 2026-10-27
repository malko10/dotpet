package com.dotpet.pet;

/** High-level behavior state used to select the pet's animation. */
public enum PetState {
    IDLE,
    WALKING,
    RUNNING,
    SLEEPING,
    FALLING,
    WALL_HIT(320_000_000L),
    LANDING(1_000_000_000L);

    private final long reactionDurationNanos;

    PetState() { this(0); }

    PetState(long reactionDurationNanos) { this.reactionDurationNanos = reactionDurationNanos; }

    public boolean isReaction() { return reactionDurationNanos > 0; }

    public long getReactionDurationNanos() { return reactionDurationNanos; }
}
