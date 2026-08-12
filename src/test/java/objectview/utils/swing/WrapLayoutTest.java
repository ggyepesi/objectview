package objectview.utils.swing;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A toolbar reports the height its rows actually need.
 *
 * <p>FlowLayout wraps its content but sizes itself for ONE row. In BorderLayout.NORTH —
 * where the parent grants exactly the preferred height — the wrapped rows are laid out
 * below the visible area and clipped. The user does not see a cramped toolbar; they see
 * a toolbar that appears complete while some of its controls have silently vanished,
 * which is how the Values scope selector became impossible to find.
 */
class WrapLayoutTest {

    private static JPanel barOf(java.awt.LayoutManager layout, int buttons, int width) {
        JPanel bar = new JPanel(layout);
        for (int i = 0; i < buttons; i++) {
            JButton button = new JButton("control " + i);
            button.setPreferredSize(new Dimension(100, 20));
            bar.add(button);
        }
        bar.setSize(width, 1);
        return bar;
    }

    @Test void aRowTooWideToFitReportsTheHeightOfEveryRowItNeeds() {
        // Eight 100px controls in a 300px bar: three rows' worth.
        JPanel wrapping = barOf(new WrapLayout(FlowLayout.LEFT, 6, 4), 8, 300);
        JPanel plain = barOf(new FlowLayout(FlowLayout.LEFT, 6, 4), 8, 300);

        int wrapped = wrapping.getPreferredSize().height;
        int single = plain.getPreferredSize().height;

        assertTrue(wrapped > single,
                   "the wrapping bar must ask for more than one row's height; "
                           + "wrapped=" + wrapped + " plain=" + single);
        assertTrue(wrapped >= single * 2,
                   "eight 100px controls in 300px need at least three rows");
    }

    /** Nothing changes for a bar that already fits — the fix must not add blank space
     *  to every toolbar in the application. */
    @Test void aRowThatFitsIsSizedExactlyAsBefore() {
        JPanel wrapping = barOf(new WrapLayout(FlowLayout.LEFT, 6, 4), 2, 800);
        JPanel plain = barOf(new FlowLayout(FlowLayout.LEFT, 6, 4), 2, 800);

        assertEquals(plain.getPreferredSize().height,
                     wrapping.getPreferredSize().height);
    }

    /** The case that hid the control: in BorderLayout.NORTH the parent grants the
     *  preferred height, so a bar that under-reports has its later rows cut off. */
    @Test void inABorderLayoutNorthEveryRowFitsInTheGrantedHeight() throws Exception {
        JPanel bar = barOf(new WrapLayout(FlowLayout.LEFT, 6, 4), 8, 300);
        JPanel host = new JPanel(new BorderLayout());
        host.add(bar, BorderLayout.NORTH);
        host.setSize(300, 400);
        javax.swing.SwingUtilities.invokeAndWait(host::doLayout);

        int lowest = 0;
        for (java.awt.Component control : bar.getComponents()) {
            lowest = Math.max(lowest, control.getY() + control.getHeight());
        }
        assertTrue(lowest <= bar.getHeight(),
                   "a control laid out below the bar's height is invisible: lowest="
                           + lowest + " barHeight=" + bar.getHeight());
    }
}
