package com.dotpet.pet;

/** Uniform source pixels, aligned to the monitor's physical pixel grid. */
public final class PixelSizing {
    public static final double BASE_SIZE = 128;
    private PixelSizing() { }

    public static Layout fit(int width, int height, double viewportWidth, double viewportHeight, double outputScale) {
        double device = Double.isFinite(outputScale) && outputScale > 0 ? outputScale : 1;
        double pixels = Math.max(1, Math.floor(Math.min(viewportWidth / width, viewportHeight / height) * device + 0.000001));
        double unit = pixels / device;
        return new Layout(unit, Math.round((viewportWidth - width * unit) * device / 2) / device,
                Math.round((viewportHeight - height * unit) * device / 2) / device);
    }

    public record Layout(double unit, double x, double y) { }
}
