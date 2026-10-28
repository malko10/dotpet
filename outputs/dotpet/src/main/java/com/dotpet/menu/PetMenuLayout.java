package com.dotpet.menu;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/** Positions an arc directly around the pet, without shifting it over the sprite. */
final class PetMenuLayout {
    static final double BUTTON_WIDTH = 92;
    static final double BUTTON_HEIGHT = 78;
    private static final double PET_GAP = 12;

    private PetMenuLayout() { }

    static Layout create(double petX, double petY, double petSize, Rectangle2D screen) {
        return create(petX, petY, petSize, screen, 4);
    }

    static Layout create(double petX, double petY, double petSize, Rectangle2D screen, int count) {
        if (count < 2) throw new IllegalArgumentException("An arc needs at least two buttons.");
        double centerX = petX + petSize / 2;
        double centerY = petY + petSize / 2;
        double baseRadius = Math.hypot(petSize / 2 + BUTTON_WIDTH / 2 + PET_GAP,
                petSize / 2 + BUTTON_HEIGHT / 2 + PET_GAP);
        double[] spans = count == 3 ? new double[] {130, 110, 90, 70} : new double[] {150, 130, 110, 90, 75, 60};
        for (double arcDegrees : spans) {
            double step = Math.toRadians(arcDegrees / (count - 1));
            double radius = Math.max(baseRadius,
                    Math.hypot(BUTTON_WIDTH + 10, BUTTON_HEIGHT + 10) / (2 * Math.sin(step / 2)));
            for (int turn = 0; turn <= 72; turn++) {
                double offset = Math.toRadians(((turn + 1) / 2) * 5) * (turn % 2 == 0 ? -1 : 1);
                double heading = -Math.PI / 2 + offset;
                List<Point2D> centers = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    double angle = heading - Math.toRadians(arcDegrees / 2) + index * step;
                    centers.add(new Point2D(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle)));
                }
                List<Point2D> envelope = new ArrayList<>(centers);
                double start = heading - Math.toRadians(arcDegrees / 2), end = heading + Math.toRadians(arcDegrees / 2);
                for (int quadrant = -8; quadrant <= 8; quadrant++) {
                    double angle = quadrant * Math.PI / 2;
                    if (angle >= start && angle <= end)
                        envelope.add(new Point2D(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle)));
                }
                Layout layout = layoutOf(centers, envelope);
                if (fits(layout, petX, petY, petSize, screen)) return layout;
            }
        }
        throw new IllegalArgumentException("화면에 메뉴를 펼칠 공간이 부족합니다.");
    }

    private static Layout layoutOf(List<Point2D> centers, List<Point2D> envelope) {
        double left = envelope.stream().mapToDouble(Point2D::getX).min().orElseThrow() - BUTTON_WIDTH / 2;
        double top = envelope.stream().mapToDouble(Point2D::getY).min().orElseThrow() - BUTTON_HEIGHT / 2;
        double right = envelope.stream().mapToDouble(Point2D::getX).max().orElseThrow() + BUTTON_WIDTH / 2;
        double bottom = envelope.stream().mapToDouble(Point2D::getY).max().orElseThrow() + BUTTON_HEIGHT / 2;
        return new Layout(List.copyOf(centers), new Rectangle2D(left, top, right - left, bottom - top));
    }

    private static boolean fits(Layout layout, double petX, double petY, double petSize, Rectangle2D screen) {
        Rectangle2D bounds = layout.bounds();
        double tolerance = 0.001;
        if (bounds.getMinX() < screen.getMinX() - tolerance || bounds.getMinY() < screen.getMinY() - tolerance
                || bounds.getMaxX() > screen.getMaxX() + tolerance || bounds.getMaxY() > screen.getMaxY() + tolerance) return false;
        Rectangle2D pet = new Rectangle2D(petX - PET_GAP, petY - PET_GAP, petSize + PET_GAP * 2, petSize + PET_GAP * 2);
        List<Rectangle2D> buttons = new ArrayList<>();
        for (Point2D point : layout.centers()) {
            Rectangle2D button = new Rectangle2D(point.getX() - BUTTON_WIDTH / 2, point.getY() - BUTTON_HEIGHT / 2,
                    BUTTON_WIDTH, BUTTON_HEIGHT);
            if (button.intersects(pet)) return false;
            for (Rectangle2D previous : buttons) if (button.intersects(previous)) return false;
            buttons.add(button);
        }
        return true;
    }

    record Layout(List<Point2D> centers, Rectangle2D bounds) { }
}
