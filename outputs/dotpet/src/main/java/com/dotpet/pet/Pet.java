package com.dotpet.pet;

import javafx.geometry.Rectangle2D;

/** Mutable runtime model for the currently displayed desktop pet. */
public final class Pet {
    private double x;
    private double y;
    private double size;
    private boolean movementEnabled = true;
    private boolean facingRight = true;
    private PetState state = PetState.IDLE;

    public Pet(double x, double y, double size) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("Pet position and size must be finite, and size must be positive.");
        }
        this.x = x;
        this.y = y;
        this.size = size;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getSize() {
        return size;
    }

    public void resize(double newSize, Rectangle2D bounds) {
        if (!Double.isFinite(newSize) || newSize <= 0) throw new IllegalArgumentException("Pet size must be positive and finite.");
        size = newSize;
        moveToClamped(x, y, bounds);
    }

    public boolean isMovementEnabled() {
        return movementEnabled;
    }

    public boolean isFacingRight() {
        return facingRight;
    }

    public PetState getState() {
        return state;
    }

    public void moveToClamped(double nextX, double nextY, Rectangle2D bounds) {
        double maxX = Math.max(bounds.getMinX(), bounds.getMaxX() - size);
        double maxY = Math.max(bounds.getMinY(), bounds.getMaxY() - size);
        x = clamp(nextX, bounds.getMinX(), maxX);
        y = clamp(nextY, bounds.getMinY(), maxY);
    }

    void moveBy(double dx, double dy, Rectangle2D bounds) {
        moveToClamped(x + dx, y + dy, bounds);
    }

    void setMovementEnabled(boolean enabled) {
        movementEnabled = enabled;
    }

    void setState(PetState state) {
        this.state = state;
    }

    void faceRight(boolean facingRight) {
        this.facingRight = facingRight;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
