package com.dotpet.pet;

import java.util.concurrent.ThreadLocalRandom;
import javafx.geometry.Rectangle2D;

/** Controls grounded roaming, drag throws, gravity, and interaction pauses. */
public final class PetMovementController {
    private static final double WALK_SPEED = 58;
    private static final double RUN_SPEED = 128;
    private static final double GRAVITY = 1_600;
    private static final double MAX_FALL_SPEED = 1_500;
    private static final double MIN_THROW_SPEED = 220;
    private static final double MAX_THROW_SPEED = 1_700;
    private static final double KNOCKDOWN_HEIGHT_FRACTION = 0.5;
    private static final double MIN_WALL_REACTION_SPEED = 80;
    private static final double WALK_MIN_SECONDS = 2.2;
    private static final double WALK_MAX_SECONDS = 5.0;
    private static final double RUN_MIN_SECONDS = 1.0;
    private static final double RUN_MAX_SECONDS = 2.0;
    private static final double IDLE_MIN_SECONDS = 1.2;
    private static final double IDLE_MAX_SECONDS = 3.1;
    private static final double SLEEP_MIN_SECONDS = 4.0;
    private static final double SLEEP_MAX_SECONDS = 7.0;
    private static final double SLEEP_CHANCE = 0.12;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Pet pet;
    private final Rectangle2D screenBounds;
    private long phaseEndsAt;
    private long lastUpdate;
    private double direction = 1;
    private double velocityX;
    private double velocityY;
    private boolean dragging;
    private boolean airborne;
    private boolean gravityRemoved;
    private boolean interactionPaused;
    private PetState reactionState;
    private double reactionSecondsRemaining;
    private double highestAirborneFootY;

    public PetMovementController(Pet pet, Rectangle2D screenBounds) {
        this.pet = pet;
        this.screenBounds = screenBounds;
        pet.moveToClamped(pet.getX(), getGroundY(), screenBounds);
        highestAirborneFootY = screenBounds.getMaxY();
        scheduleIdle(System.nanoTime());
    }

    public Rectangle2D getScreenBounds() {
        return screenBounds;
    }

    public double getGroundY() {
        return Math.max(screenBounds.getMinY(), screenBounds.getMaxY() - pet.getSize());
    }

    public boolean isGravityRemoved() {
        return gravityRemoved;
    }

    /** Recovery progress follows the physics clock, including menu pauses. */
    public double getLandingProgress() {
        if (reactionState != PetState.LANDING) return 0;
        double duration = PetState.LANDING.getReactionDurationNanos() / (double) NANOS_PER_SECOND;
        return Math.max(0, Math.min(1, 1 - reactionSecondsRemaining / duration));
    }

    public void update(long now) {
        double elapsedSeconds = lastUpdate == 0
                ? 0
                : Math.min(Math.max(0, now - lastUpdate) / (double) NANOS_PER_SECOND, 0.05);
        lastUpdate = now;

        if (dragging || interactionPaused) {
            return;
        }
        if (gravityRemoved) {
            pet.setState(PetState.IDLE);
            return;
        }
        if (advanceReaction(elapsedSeconds, now)) return;
        if (airborne || pet.getY() < getGroundY() - 0.5) {
            if (!airborne) highestAirborneFootY = pet.getY() + pet.getSize();
            airborne = true;
            updateProjectile(elapsedSeconds, now);
            return;
        }
        if (!pet.isMovementEnabled()) {
            pet.setState(PetState.IDLE);
            return;
        }

        if (now >= phaseEndsAt) {
            advanceBehavior(now);
        }

        if (pet.getState() == PetState.WALKING || pet.getState() == PetState.RUNNING) {
            double speed = pet.getState() == PetState.RUNNING ? RUN_SPEED : WALK_SPEED;
            pet.moveBy(direction * speed * elapsedSeconds, 0, screenBounds);
            pet.moveToClamped(pet.getX(), getGroundY(), screenBounds);
            reflectAtScreenEdges();
        } else {
            pet.moveToClamped(pet.getX(), getGroundY(), screenBounds);
        }
    }

    public void setMovementEnabled(boolean enabled) {
        pet.setMovementEnabled(enabled);
        if (enabled && reactionState == null) {
            scheduleIdle(System.nanoTime() + secondsToNanos(0.6));
        } else if (!airborne && reactionState == null) {
            pet.setState(PetState.IDLE);
        }
        lastUpdate = 0;
    }

    /** Removes gravity and freezes the pet at its current location until restored. */
    public void setGravityRemoved(boolean removed) {
        clearReaction();
        gravityRemoved = removed;
        highestAirborneFootY = pet.getY() + pet.getSize();
        airborne = false;
        velocityX = 0;
        velocityY = 0;
        lastUpdate = 0;
        if (removed) {
            pet.setState(PetState.IDLE);
        } else if (pet.getY() < getGroundY() - 0.5) {
            airborne = true;
            pet.setState(PetState.FALLING);
        } else if (pet.isMovementEnabled()) {
            scheduleIdle(System.nanoTime() + secondsToNanos(0.6));
        }
    }

    public void setInteractionPaused(boolean paused) {
        interactionPaused = paused;
        lastUpdate = 0;
    }

    public void beginDrag() {
        clearReaction();
        dragging = true;
        highestAirborneFootY = pet.getY() + pet.getSize();
        airborne = false;
        velocityX = 0;
        velocityY = 0;
        pet.setState(PetState.IDLE);
    }

    public void endDrag(double throwVelocityX, double throwVelocityY) {
        clearReaction();
        dragging = false;
        lastUpdate = 0;
        highestAirborneFootY = pet.getY() + pet.getSize();
        if (gravityRemoved) {
            pet.setState(PetState.IDLE);
            return;
        }

        double speed = Math.hypot(throwVelocityX, throwVelocityY);
        if (speed >= MIN_THROW_SPEED) {
            double scale = Math.min(1, MAX_THROW_SPEED / speed);
            velocityX = throwVelocityX * scale;
            velocityY = throwVelocityY * scale;
            if (Math.abs(velocityX) > 1) direction = Math.signum(velocityX);
            airborne = true;
            pet.faceRight(velocityX >= 0);
            pet.setState(PetState.FALLING);
        } else if (pet.getY() < getGroundY() - 0.5) {
            velocityX = 0;
            velocityY = 0;
            airborne = true;
            pet.setState(PetState.FALLING);
        } else {
            velocityX = 0;
            velocityY = 0;
            pet.moveToClamped(pet.getX(), getGroundY(), screenBounds);
            pet.setState(PetState.IDLE);
            if (pet.isMovementEnabled()) {
                scheduleIdle(System.nanoTime() + secondsToNanos(0.8));
            }
        }
    }

    private void advanceBehavior(long now) {
        switch (pet.getState()) {
            case WALKING, RUNNING -> {
                pet.setState(PetState.IDLE);
                scheduleIdle(now);
            }
            case SLEEPING -> {
                pet.setState(PetState.IDLE);
                scheduleIdle(now);
            }
            case IDLE -> {
                if (ThreadLocalRandom.current().nextDouble() < SLEEP_CHANCE) {
                    pet.setState(PetState.SLEEPING);
                    phaseEndsAt = now + secondsToNanos(randomBetween(SLEEP_MIN_SECONDS, SLEEP_MAX_SECONDS));
                } else if (ThreadLocalRandom.current().nextDouble() < 0.22) {
                    pet.faceRight(direction > 0);
                    pet.setState(PetState.RUNNING);
                    phaseEndsAt = now + secondsToNanos(randomBetween(RUN_MIN_SECONDS, RUN_MAX_SECONDS));
                } else {
                    direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                    pet.faceRight(direction > 0);
                    pet.setState(PetState.WALKING);
                    phaseEndsAt = now + secondsToNanos(randomBetween(WALK_MIN_SECONDS, WALK_MAX_SECONDS));
                }
            }
            case FALLING -> {
                airborne = true;
                updateProjectile(0, now);
            }
            case WALL_HIT, LANDING -> {
                pet.setState(PetState.IDLE);
                scheduleIdle(now);
            }
        }
    }

    private void updateProjectile(double elapsedSeconds, long now) {
        highestAirborneFootY = Math.min(highestAirborneFootY, pet.getY() + pet.getSize());
        pet.setState(reactionState == PetState.WALL_HIT ? PetState.WALL_HIT : PetState.FALLING);
        velocityY = Math.min(MAX_FALL_SPEED, velocityY + GRAVITY * elapsedSeconds);

        double nextX = pet.getX() + velocityX * elapsedSeconds;
        double nextY = pet.getY() + velocityY * elapsedSeconds;
        double minX = screenBounds.getMinX();
        double maxX = Math.max(minX, screenBounds.getMaxX() - pet.getSize());
        double minY = screenBounds.getMinY();

        if (nextX < minX) {
            nextX = minX;
            boolean react = Math.abs(velocityX) >= MIN_WALL_REACTION_SPEED;
            velocityX = Math.abs(velocityX) * 0.45;
            direction = 1;
            pet.faceRight(!react);
            if (react) startReaction(PetState.WALL_HIT);
        } else if (nextX > maxX) {
            nextX = maxX;
            boolean react = Math.abs(velocityX) >= MIN_WALL_REACTION_SPEED;
            velocityX = -Math.abs(velocityX) * 0.45;
            direction = -1;
            pet.faceRight(react);
            if (react) startReaction(PetState.WALL_HIT);
        }
        if (nextY < minY) {
            nextY = minY;
            velocityY = Math.abs(velocityY) * 0.25;
        }

        highestAirborneFootY = Math.min(highestAirborneFootY, nextY + pet.getSize());
        if (nextY >= getGroundY() && velocityY >= 0) {
            double dropHeight = screenBounds.getMaxY() - highestAirborneFootY;
            pet.moveToClamped(nextX, getGroundY(), screenBounds);
            velocityX = 0;
            velocityY = 0;
            airborne = false;
            clearReaction();
            if (dropHeight >= screenBounds.getHeight() * KNOCKDOWN_HEIGHT_FRACTION) startReaction(PetState.LANDING);
            else {
                pet.setState(PetState.IDLE);
                if (pet.isMovementEnabled()) scheduleIdle(now);
            }
            return;
        }
        pet.moveToClamped(nextX, nextY, screenBounds);
    }

    private void reflectAtScreenEdges() {
        double maxX = Math.max(screenBounds.getMinX(), screenBounds.getMaxX() - pet.getSize());
        if (pet.getX() <= screenBounds.getMinX() && direction < 0) {
            direction = 1;
            pet.faceRight(false);
            startReaction(PetState.WALL_HIT);
        } else if (pet.getX() >= maxX && direction > 0) {
            direction = -1;
            pet.faceRight(true);
            startReaction(PetState.WALL_HIT);
        }
    }

    private void startReaction(PetState state) {
        reactionState = state;
        reactionSecondsRemaining = state.getReactionDurationNanos() / (double) NANOS_PER_SECOND;
        pet.setState(state);
    }

    private void clearReaction() {
        reactionState = null;
        reactionSecondsRemaining = 0;
    }

    private boolean advanceReaction(double elapsedSeconds, long now) {
        if (reactionState == null) return false;
        reactionSecondsRemaining = Math.max(0, reactionSecondsRemaining - elapsedSeconds);
        if (reactionSecondsRemaining > 0) {
            if (airborne && reactionState == PetState.WALL_HIT) return false;
            pet.setState(reactionState);
            pet.moveToClamped(pet.getX(), getGroundY(), screenBounds);
            return true;
        }
        clearReaction();
        if (airborne) {
            if (Math.abs(velocityX) > 1) pet.faceRight(velocityX > 0);
            pet.setState(PetState.FALLING);
            return false;
        }
        pet.faceRight(direction > 0);
        pet.setState(PetState.IDLE);
        scheduleIdle(now);
        return true;
    }

    private void scheduleIdle(long startTime) {
        phaseEndsAt = startTime + secondsToNanos(randomBetween(IDLE_MIN_SECONDS, IDLE_MAX_SECONDS));
    }

    private static double randomBetween(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private static long secondsToNanos(double seconds) {
        return (long) (seconds * NANOS_PER_SECOND);
    }
}
