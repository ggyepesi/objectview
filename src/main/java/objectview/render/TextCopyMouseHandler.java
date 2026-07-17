package objectview.render;


import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * One shared listener object for all manually-painted text components.
 */
public final class TextCopyMouseHandler extends MouseAdapter {

    public static final TextCopyMouseHandler INSTANCE =
            new TextCopyMouseHandler();

    private TextCopyMouseHandler() {}

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopup(e);
            return;
        }

        Object source = e.getSource();

        if (source instanceof TextRow row) {
            row.beginSelection(e.getPoint());
        } else if (source instanceof TextBlock block) {
            block.beginSelection(e.getPoint());
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Object source = e.getSource();

        if (source instanceof TextRow row) {
            row.updateSelection(e.getPoint());
        } else if (source instanceof TextBlock block) {
            block.updateSelection(e.getPoint());
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopup(e);
            return;
        }

        Object source = e.getSource();

        if (source instanceof TextRow row) {
            row.endSelection(e.getPoint());
        } else if (source instanceof TextBlock block) {
            block.endSelection(e.getPoint());
        }
    }

    private void showPopup(MouseEvent e) {
        Object source = e.getSource();

        if (source instanceof TextRow row) {
            row.showCopyPopup(e.getPoint());
        } else if (source instanceof TextBlock block) {
            block.showCopyPopup(e.getPoint());
        }
    }
}
