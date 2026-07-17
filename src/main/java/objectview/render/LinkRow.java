package objectview.render;

import objectview.utils.BrowserLauncher;
import objectview.search.SearchPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Link row implemented on top of TextRow, so it inherits:
 * selection, search highlighting, wrapping, keyboard copy, and popup copy.
 */
public class LinkRow extends TextRow {

    private static final Color LINK_COLOR =
            new Color(0, 80, 200);

    private final String url;
    private final String label;

    public LinkRow(
            String fieldName,
            List<String> fieldPath,
            String rawValue,
            String annotationText) {

        this(
                fieldName,
                fieldPath,
                parse(rawValue, annotationText));
    }

    private LinkRow(
            String fieldName,
            List<String> fieldPath,
            ParsedLink parsed) {

        super(
                fieldName,
                fieldPath == null ? List.of() : new ArrayList<>(fieldPath),
                List.of(parsed.label()));

        this.url = parsed.url();
        this.label = parsed.label();

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(this.url + " · click to open · right-click to copy");

        putClientProperty(
                SearchPanel.FIELD_VALUE_PROPERTY,
                this.url);
    }

    @Override
    protected Color valueColor() {
        return LINK_COLOR;
    }

    @Override
    protected boolean underlineValue() {
        return true;
    }

    @Override
    protected int underlineThickness() {
        return 1;
    }

    @Override
    protected void valueClicked(java.awt.event.MouseEvent e) {
        if (url != null && !url.isBlank()) {
            BrowserLauncher.open(url);
        }
    }

    @Override
    protected void addExtraCopyMenuItems(JPopupMenu menu) {
        menu.addSeparator();

        JMenuItem copyUrl =
                new JMenuItem("Copy URL");
        copyUrl.addActionListener(e ->
                                          copyToClipboard(url));
        menu.add(copyUrl);

        if (label != null
                && !label.isBlank()
                && !label.equals(url)) {

            JMenuItem copyLabel =
                    new JMenuItem("Copy label");
            copyLabel.addActionListener(e ->
                                                copyToClipboard(label));
            menu.add(copyLabel);
        }
    }

    private static ParsedLink parse(
            String rawValue,
            String annotationText) {

        String value =
                rawValue == null ? "" : rawValue.trim();

        String valueLabel = null;

        int bar = value.indexOf('|');
        if (bar > 0 && bar < value.length() - 1) {
            valueLabel = value.substring(0, bar).trim();
            value = value.substring(bar + 1).trim();
        }

        String label =
                annotationText != null && !annotationText.isBlank()
                        ? annotationText.trim()
                        : valueLabel != null && !valueLabel.isBlank()
                          ? valueLabel
                          : value;

        return new ParsedLink(value, label);
    }

    private record ParsedLink(
            String url,
            String label) {
    }
}