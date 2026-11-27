package objectview.render;

import objectview.field.LinkedTextSequence;
import objectview.utils.BrowserLauncher;
import objectview.utils.swing.SwingWindowActivation;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

/** Opens the rendered value for reading and copying without changing the card or its selection. */
final class TextReader {
    static final String ACTION = "Read full text…";
    private static final String LINK = "objectview.textReader.url";

    private TextReader() { }

    static void addAction(JPopupMenu menu, JComponent owner, String field, CharSequence value) {
        if (value == null || value.toString().isBlank()) return;
        var item = new JMenuItem(ACTION);
        item.addActionListener(event -> open(owner, field, value));
        menu.add(item);
    }

    private static void open(JComponent owner, String field, CharSequence value) {
        var window = SwingUtilities.getWindowAncestor(owner);
        String title = field == null || field.isBlank() ? "Full text" : field + " — Full text";
        var dialog = new JDialog(window, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new JScrollPane(pane(value)));
        dialog.setSize(760, 540);
        dialog.setLocationRelativeTo(window);
        SwingWindowActivation.showAndFocus(dialog);
    }

    static JTextPane pane(CharSequence value) {
        JTextPane pane = new JTextPane() {
            @Override public String getToolTipText(MouseEvent event) {
                return event == null ? null : linkAt(this, event.getPoint());
            }
        };
        Font font = pane.getFont();
        pane.setFont(font.deriveFont(Math.max(17f, font.getSize2D() + 3)));
        pane.setEditable(false);
        pane.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        if (value instanceof LinkedTextSequence linked) {
            var document = pane.getStyledDocument();
            Color linkColor = UIManager.getColor("Component.linkColor");
            var linkStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(linkStyle, linkColor == null ? new Color(0x1859a5) : linkColor);
            StyleConstants.setUnderline(linkStyle, true);
            try {
                for (var part : linked.parts()) {
                    var style = part.linked() ? new SimpleAttributeSet(linkStyle) : null;
                    if (style != null) style.addAttribute(LINK, part.url());
                    document.insertString(document.getLength(), part.text(), style);
                }
            } catch (BadLocationException impossible) {
                throw new IllegalStateException(impossible);
            }
            ToolTipManager.sharedInstance().registerComponent(pane);
            MouseAdapter links = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    pane.setCursor(Cursor.getPredefinedCursor(linkAt(pane, event.getPoint()) == null
                            ? Cursor.TEXT_CURSOR : Cursor.HAND_CURSOR));
                }

                @Override public void mouseClicked(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 1
                            || pane.getSelectionStart() != pane.getSelectionEnd()) return;
                    String url = linkAt(pane, event.getPoint());
                    if (url != null) BrowserLauncher.open(url);
                }
            };
            pane.addMouseMotionListener(links);
            pane.addMouseListener(links);
        } else {
            pane.setText(value.toString());
        }
        pane.setCaretPosition(0);
        return pane;
    }

    private static String linkAt(JTextPane pane, Point point) {
        var document = pane.getStyledDocument();
        int offset = pane.viewToModel2D(point);
        // Swing returns the nearest caret position, even in blank space. Only accept a
        // character on the pointed-at line, including a click just left of its ending caret.
        for (int i = offset; i >= Math.max(0, offset - 1); i--) {
            if (i >= document.getLength()) continue;
            try {
                Rectangle2D start = pane.modelToView2D(i);
                Rectangle2D end = pane.modelToView2D(i + 1);
                if (start != null && end != null && start.getY() == end.getY()
                        && point.y >= start.getY() && point.y < start.getMaxY()
                        && point.x >= start.getX() && point.x < end.getX()) {
                    return (String) document.getCharacterElement(i).getAttributes().getAttribute(LINK);
                }
            } catch (BadLocationException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        return null;
    }
}
