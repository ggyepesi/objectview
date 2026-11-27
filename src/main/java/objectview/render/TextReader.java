package objectview.render;

import objectview.Viewable;
import objectview.field.FieldPath;
import objectview.field.LinkedTextSequence;
import objectview.utils.swing.SwingWindowActivation;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JRootPane;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

/** Opens the rendered value for reading and copying without changing the card or its selection. */
final class TextReader {
    static final String ACTION = "Read full text…";
    private static final String LINK = "objectview.textReader.link";

    private static final Object READERS = new Object();

    private TextReader() { }

    static void addAction(JPopupMenu menu, JComponent owner, String field,
                          FieldPath path, CharSequence value) {
        if (value == null || value.toString().isBlank()) return;
        var item = new JMenuItem(ACTION);
        item.addActionListener(event -> open(owner, address(owner, field, path), value));
        menu.add(item);
    }

    record Address(FieldKey key, String title) { }
    record FieldKey(Object instance, FieldPath path) { }

    static Address address(JComponent source, String label, FieldPath path) {
        var host = RenderedInstanceHost.hostOf(source);
        FieldPath fieldPath = path == null ? FieldPath.ROOT : path;
        Viewable instance = host == null ? null : host.renderedInstance();
        var identity = RenderContext.identityOf(instance);
        Object ownerKey = identity == null ? (host == null ? source : host) : identity;
        if (host != null) {
            FieldPath prefix = host.renderedInstancePath();
            if (fieldPath.startsWith(prefix)) {
                fieldPath = new FieldPath(fieldPath.segments().subList(prefix.size(), fieldPath.size()));
            }
        }
        String field = label == null || label.isBlank() ? fieldPath.leaf() : label;
        String caption = host == null ? "" : host.renderedInstanceCaption();
        if ((caption == null || caption.isBlank()) && instance != null) {
            caption = instance.typeName();
            String id = instance.getIdentifier();
            if (id != null && !id.isBlank()) caption = caption + " " + id;
        }
        String title = field.isBlank() ? "Full text" : field + " — Full text";
        if (caption != null && !caption.isBlank()) title = caption.strip() + " — " + title;
        return new Address(new FieldKey(ownerKey, fieldPath), title);
    }

    /** Shared by every card/row in this window, independent of virtualization. */
    static final class Readers<T> {
        private final Map<FieldKey, T> open = new HashMap<>();

        T forField(FieldKey key, Supplier<T> create) {
            return open.computeIfAbsent(key, ignored -> create.get());
        }

        void closed(FieldKey key, T reader) {
            open.remove(key, reader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Readers<Reader> readers(JRootPane root) {
        var readers = (Readers<Reader>) root.getClientProperty(READERS);
        if (readers == null) {
            readers = new Readers<>();
            root.putClientProperty(READERS, readers);
        }
        return readers;
    }

    private static void open(JComponent owner, Address address, CharSequence value) {
        JRootPane root = SwingUtilities.getRootPane(owner);
        if (root == null) return;
        var readers = readers(root);
        var window = SwingUtilities.getWindowAncestor(owner);
        Reader reader = readers.forField(address.key(), () -> new Reader(window));
        // A close event may still be queued when the menu is opened again. Its
        // cleanup must not remove the replacement registered for the same field.
        if (reader.closed) {
            readers.closed(address.key(), reader);
            reader = readers.forField(address.key(), () -> new Reader(window));
        }
        Reader active = reader;
        active.show(address.title(), value, RenderedInstanceHost.hostOf(owner),
                () -> readers.closed(address.key(), active));
    }

    private static final class Reader extends JDialog {
        private boolean closed;
        private Object rendered;
        private RenderContext context;
        private Viewable origin;
        private Runnable onClose = () -> { };

        Reader(java.awt.Window owner) {
            super(owner, Dialog.ModalityType.MODELESS);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            setSize(760, 540);
            setLocationRelativeTo(owner);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { onClose.run(); }
            });
        }

        void show(String title, CharSequence value, RenderedInstanceHost host, Runnable closed) {
            onClose = closed;
            context = host == null ? null : host.renderedInstanceContext();
            origin = host == null ? null : host.renderedInstance();
            setTitle(title);
            Object content = value instanceof LinkedTextSequence linked ? linked.parts() : value.toString();
            if (!Objects.equals(rendered, content)) {
                rendered = content;
                setContentPane(new JScrollPane(pane(value, this::follow)));
                validate();
            }
            SwingWindowActivation.showAndFocus(this);
        }

        private void follow(LinkedTextSequence.Part link, JTextPane position) {
            TextLinkNavigation.open(link, context, target -> {
                if (context == null || !context.focusFromReader(target, position, origin,
                        () -> !closed && isDisplayable(),
                        () -> SwingWindowActivation.showAndFocus(this))) return false;
                // An owned dialog stays above its owner, even after toFront() on the
                // instance window. Keep the reader alive but let the target be seen.
                setVisible(false);
                return true;
            });
        }

        @Override public void dispose() {
            closed = true;
            if (context != null) context.updateBack();
            super.dispose();
        }
    }

    static JTextPane pane(CharSequence value) {
        return pane(value, (part, origin) -> TextLinkNavigation.open(part, null, ignored -> false));
    }

    static JTextPane pane(CharSequence value,
            BiConsumer<LinkedTextSequence.Part, JTextPane> follow) {
        JTextPane pane = new JTextPane() {
            @Override public String getToolTipText(MouseEvent event) {
                return event == null ? null : TextLinkNavigation.destination(linkAt(this, event.getPoint()));
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
                    if (style != null) style.addAttribute(LINK, part);
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
                    var link = linkAt(pane, event.getPoint());
                    if (link != null) follow.accept(link, pane);
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

    private static LinkedTextSequence.Part linkAt(JTextPane pane, Point point) {
        var document = pane.getStyledDocument();
        int offset = pane.viewToModel2D(point);
        // Swing returns the nearest caret position, even in blank space. Only accept a
        // character on the pointed-at line, including a click just left of its ending caret.
        for (int i = offset; i >= Math.max(0, offset - 1); i--) {
            if (i >= document.getLength()) continue;
            try {
                Rectangle2D start = pane.modelToView2D(i);
                Rectangle2D end = pane.getUI().modelToView2D(pane, i + 1,
                        javax.swing.text.Position.Bias.Backward);
                if (start != null && end != null && start.getY() == end.getY()
                        && point.y >= start.getY() && point.y < start.getMaxY()
                        && point.x >= start.getX() && point.x < end.getX()) {
                    return (LinkedTextSequence.Part) document.getCharacterElement(i).getAttributes().getAttribute(LINK);
                }
            } catch (BadLocationException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        return null;
    }
}
