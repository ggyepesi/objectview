package objectview.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Shared instance highlight/selection paint policy; layouts choose only geometry. */
public final class InstancePaint {
    private InstancePaint() {}

    public static final Color SELECTION_TINT = new Color(30, 110, 210, 28);
    public static final Color SELECTION_BORDER = new Color(30, 110, 210);

    public static void fillHighlight(
            Graphics graphics, Color highlight, int width, int height) {
        if (highlight == null) return;
        graphics.setColor(highlight);
        graphics.fillRect(0, 0, width, height);
    }

    /** Paint after children so images and other opaque field components cannot hide selection. */
    public static void paintSelection(
            Graphics graphics, int width, int height, boolean rounded) {
        graphics.setColor(SELECTION_TINT);
        graphics.fillRect(0, 0, width, height);
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setColor(SELECTION_BORDER);
            g2.setStroke(new BasicStroke(2f));
            if (rounded) {
                g2.drawRoundRect(1, 1, width - 3, height - 3, 8, 8);
            } else {
                g2.drawRect(1, 1, width - 3, height - 3);
            }
        } finally {
            g2.dispose();
        }
    }
}
