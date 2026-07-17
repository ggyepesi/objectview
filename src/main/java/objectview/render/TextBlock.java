package objectview.render;

import objectview.field.FieldProperties;
import objectview.text.TextSelectable;
import objectview.text.TextSelection;
import objectview.text.TextSelectionManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class TextBlock extends JComponent implements TextSelectable {

    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final int GAP = 8;
    private static final int ROW_GAP = 2;
    private static final int MAX_PREF_WIDTH = 460;

    private static final Color SEARCH_HIGHLIGHT =
            new Color(255, 245, 120);
    private static final Color SELECTION_BACKGROUND =
            new Color(80, 140, 255);
    private static final Color SELECTION_FOREGROUND =
            Color.WHITE;
    // Shown while a copy popup item is hovered, so it's visible up-front what
    // "Copy block" / "Copy row" will actually take.
    private static final Color PREVIEW_BACKGROUND =
            new Color(198, 219, 255);

    private final Set<Integer> previewLines = new HashSet<>();

    @Override
    public void clearSelectionFromManager() {
        selection.clear();
        repaint();
    }

    public record Row(String fieldName,
                      List<String> fieldPath,
                      Object value,
                      List<String> lines) {
    }

    private record PaintLine(Row row,
                             String text,
                             int x,
                             int baseline,
                             int top,
                             int bottom,
                             int lineIndex) {
    }

    private record TextPosition(int lineIndex, int offset) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<List<String>, List<String>> highlightTokensByPath =
            new HashMap<>();
    private final TextSelection selection =
            new TextSelection();

    private int cachedWidth = -1;
    private List<PaintLine> cachedLines = new ArrayList<>();

    public TextBlock(List<Row> rows) {
        if (rows != null) {
            this.rows.addAll(rows);
        }
        Card.RenderStats.textBlocks++;
        setOpaque(false);
        setFocusable(true);

        putClientProperty(FieldProperties.FIELD_NAME_PROPERTY, "textBlock");
        putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, this);

        addMouseListener(TextCopyMouseHandler.INSTANCE);
        addMouseMotionListener(TextCopyMouseHandler.INSTANCE);
        setToolTipText("Drag to select · "
                + (Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                        == java.awt.event.InputEvent.META_DOWN_MASK
                        ? "Cmd+C" : "Ctrl+C")
                + "/right-click to copy");

        registerCopyShortcut();
    }

    private void registerCopyShortcut() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        getInputMap(WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), "copy");

        getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sel = selectedText();
                copyToClipboard(sel.isBlank() ? blockText() : sel);
            }
        });
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public void beginSelection(Point p) {
        TextSelectionManager.activate(this);

        requestFocusInWindow();
        TextPosition pos = positionAt(p);
        selection.setAnchor(pos.lineIndex(), pos.offset());
        repaint();
    }

    public void updateSelection(Point p) {
        if (!selection.hasAnchor()) {
            beginSelection(p);
            return;
        }

        TextPosition pos = positionAt(p);
        selection.setFocus(pos.lineIndex(), pos.offset());
        repaint();
    }

    public void endSelection(Point p) {
        updateSelection(p);

        // A plain click (no drag) selects the whole row under the cursor,
        // so it's always visible what Cmd+C / "Copy" will take.
        if (selection.isEmpty()) {
            selectRowAt(p);
        }
    }

    private void selectRowAt(Point p) {
        List<PaintLine> lines = computeLayout(getWidth());
        if (lines.isEmpty()) {
            return;
        }

        Row row = rowAt(p);
        PaintLine first = null;
        PaintLine last = null;

        for (PaintLine line : lines) {
            if (row == null || line.row() == row) {
                if (first == null) {
                    first = line;
                }
                last = line;
            }
        }

        if (first == null) {
            first = lines.get(0);
            last = lines.get(lines.size() - 1);
        }

        selection.setAnchor(first.lineIndex(), 0);
        selection.setFocus(last.lineIndex(), last.text().length());
        repaint();
    }

    public void showCopyPopup(Point p) {
        TextSelectionManager.activate(this);

        Row row = rowAt(p);
        JPopupMenu menu = new JPopupMenu();

        String selectedText = selectedText();
        Set<Integer> blockLines = allLineIndices();
        Set<Integer> rowLines = linesForRow(row);

        if (!selectedText.isBlank()) {
            // Existing drag-selection is already painted; previewing nothing
            // keeps that selection visible while the item is hovered.
            addCopyItem(menu, "Copy selection",
                        () -> copyToClipboard(selectedText), Set.of());
            menu.addSeparator();
        }

        addCopyItem(menu, "Copy block",
                    () -> copyToClipboard(blockText()), blockLines);

        if (row != null) {
            menu.addSeparator();
            addCopyItem(menu, "Copy row",
                        () -> copyToClipboard(rowText(row)), rowLines);
            addCopyItem(menu, "Copy value",
                        () -> copyToClipboard(rowValueText(row)), rowLines);

            if (row.fieldName() != null && !row.fieldName().isBlank()) {
                menu.addSeparator();
                addCopyItem(menu, "Copy field name",
                            () -> copyToClipboard(row.fieldName()), rowLines);
                addCopyItem(menu, "Copy field path",
                            () -> copyToClipboard(pathText(row)), rowLines);
            }
        }

        // Clear the preview once the menu goes away (selected or dismissed).
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(
                    javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(
                    javax.swing.event.PopupMenuEvent e) {
                clearPreview();
            }

            @Override
            public void popupMenuCanceled(
                    javax.swing.event.PopupMenuEvent e) {
                clearPreview();
            }
        });

        // Default highlight: whatever "Copy block" (always present) would take,
        // so it's immediately visible even before hovering an item.
        if (selectedText.isBlank()) {
            setPreviewLines(blockLines);
        }

        menu.show(this, p.x, p.y);
    }

    // Adds a copy item that, while hovered (armed), highlights the lines it
    // would copy.
    private void addCopyItem(JPopupMenu menu, String label,
                             Runnable action, Set<Integer> previewOnHover) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        item.getModel().addChangeListener(e -> {
            if (item.getModel().isArmed()) {
                setPreviewLines(previewOnHover);
            }
        });
        menu.add(item);
    }

    private void setPreviewLines(Set<Integer> lines) {
        previewLines.clear();
        if (lines != null) {
            previewLines.addAll(lines);
        }
        repaint();
    }

    private void clearPreview() {
        if (!previewLines.isEmpty()) {
            previewLines.clear();
            repaint();
        }
    }

    private Set<Integer> allLineIndices() {
        Set<Integer> out = new HashSet<>();
        for (PaintLine line : computeLayout(getWidth())) {
            out.add(line.lineIndex());
        }
        return out;
    }

    private Set<Integer> linesForRow(Row row) {
        Set<Integer> out = new HashSet<>();
        if (row == null) {
            return out;
        }
        for (PaintLine line : computeLayout(getWidth())) {
            if (line.row() == row) {
                out.add(line.lineIndex());
            }
        }
        return out;
    }

    public Row rowAt(Point p) {
        if (p == null) {
            return null;
        }

        for (PaintLine line : computeLayout(getWidth())) {
            if (p.y >= line.top() && p.y <= line.bottom()) {
                return line.row();
            }
        }

        return null;
    }

    private String selectedText() {
        if (selection.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (PaintLine line : computeLayout(getWidth())) {
            if (!selection.intersectsLine(line.lineIndex())) {
                continue;
            }

            int start = clamp(selection.selectedStartForLine(line.lineIndex()),
                              0, line.text().length());
            int end = clamp(selection.selectedEndForLine(line.lineIndex(),
                                                         line.text().length()),
                            0, line.text().length());

            if (end < start) {
                int t = start;
                start = end;
                end = t;
            }

            if (sb.length() > 0) {
                sb.append('\n');
            }

            sb.append(line.text(), start, end);
        }

        return sb.toString();
    }

    private String blockText() {
        StringBuilder sb = new StringBuilder();

        for (Row row : rows) {
            if (sb.length() > 0) {
                sb.append('\n');
            }

            sb.append(rowText(row));
        }

        return sb.toString();
    }

    private String rowText(Row row) {
        String value = rowValueText(row);
        String fieldName = row.fieldName();

        return fieldName == null || fieldName.isBlank()
                ? value
                : fieldName + ": " + value;
    }

    private String rowValueText(Row row) {
        return row.lines() == null
                ? ""
                : String.join("\n", row.lines());
    }

    private String pathText(Row row) {
        return row.fieldPath() == null
                ? ""
                : String.join(".", row.fieldPath());
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(text == null ? "" : text),
                            null);
    }

    public boolean hasMatchingRow(List<String> path, List<String> tokens) {
        for (Row row : rows) {
            if (samePath(row.fieldPath(), path)
                    && matches(row.value(), tokens)) {
                return true;
            }
        }
        return false;
    }

    public void setHighlightTokens(List<String> path, List<String> tokens) {
        highlightTokensByPath.put(copyPath(path),
                                  tokens == null ? List.of() : new ArrayList<>(tokens));
        repaint();
    }

    public void clearHighlight() {
        highlightTokensByPath.clear();
        repaint();
    }

    private Font fieldFont() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        return base.deriveFont(Font.BOLD);
    }

    private Font valueFont() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        return base;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fmField = getFontMetrics(fieldFont());
        FontMetrics fmValue = getFontMetrics(valueFont());

        int longest = 0;

        for (Row row : rows) {
            int prefixWidth = fmField.stringWidth(prefix(row));
            for (String line : row.lines()) {
                longest = Math.max(longest,
                                   prefixWidth + GAP + fmValue.stringWidth(line));
            }
        }

        int naturalWidth = Math.max(120, Math.min(MAX_PREF_WIDTH, PAD_X + longest + PAD_X));

        // Reserve height for wrapping at the width we are actually given (the
        // card stretches us past naturalWidth). Computing height at
        // naturalWidth would over-count lines and leave a gap below the text.
        int layoutWidth = getWidth() > 0 ? getWidth() : naturalWidth;
        List<PaintLine> lines = computeLayout(layoutWidth);

        int height = PAD_Y * 2
                + Math.max(1, lines.size()) * fmValue.getHeight()
                + Math.max(0, rows.size() - 1) * ROW_GAP;

        return new Dimension(naturalWidth, height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);

        // Height depends on wrap width, so re-lay-out once the real width is
        // known. Converges in one extra pass (width then stays put).
        if (widthChanged) {
            revalidate();
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        try {
            Font fieldFont = fieldFont();
            Font valueFont = valueFont();
            FontMetrics fmValue = g2.getFontMetrics(valueFont);

            Row previous = null;

            for (PaintLine pl : computeLayout(getWidth())) {
                Row row = pl.row();

                if (previewLines.contains(pl.lineIndex())) {
                    int textWidth = fmValue.stringWidth(pl.text());
                    g2.setColor(PREVIEW_BACKGROUND);
                    g2.fillRect(PAD_X, pl.top(),
                                Math.max(40, (pl.x() - PAD_X) + textWidth),
                                pl.bottom() - pl.top());
                }

                if (row != previous) {
                    String prefix = prefix(row);

                    if (!prefix.isEmpty()) {
                        g2.setFont(fieldFont);
                        g2.setColor(getForeground());
                        g2.drawString(prefix, PAD_X, pl.baseline());
                    }

                    previous = row;
                }

                g2.setFont(valueFont);
                paintTextLine(g2, pl, fmValue,
                              highlightTokensByPath.getOrDefault(row.fieldPath(),
                                                                  List.of()));
            }
        } finally {
            g2.dispose();
        }
    }

    private List<PaintLine> computeLayout(int width) {
        if (width == cachedWidth && !cachedLines.isEmpty()) {
            return cachedLines;
        }

        FontMetrics fmField = getFontMetrics(fieldFont());
        FontMetrics fmValue = getFontMetrics(valueFont());

        List<PaintLine> out = new ArrayList<>();
        int y = PAD_Y + fmValue.getAscent();
        int lineIndex = 0;

        for (Row row : rows) {
            String prefix = prefix(row);
            int prefixWidth = prefix.isEmpty() ? 0 : fmField.stringWidth(prefix) + GAP;
            int valueX = PAD_X + prefixWidth;
            int valueWidth = Math.max(80, width - valueX - PAD_X);

            List<String> wrapped = new ArrayList<>();

            for (String line : row.lines()) {
                wrapOneLine(line, fmValue, valueWidth, wrapped);
            }

            if (wrapped.isEmpty()) {
                wrapped.add("");
            }

            for (String line : wrapped) {
                out.add(new PaintLine(row, line, valueX, y,
                                      y - fmValue.getAscent(),
                                      y + fmValue.getDescent(),
                                      lineIndex++));
                y += fmValue.getHeight();
            }

            y += ROW_GAP;
        }

        cachedWidth = width;
        cachedLines = out;
        return out;
    }

    private void wrapOneLine(String line,
                             FontMetrics fm,
                             int maxWidth,
                             List<String> out) {
        if (line == null || line.isBlank()) {
            out.add("");
            return;
        }

        String[] words = line.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            // A single word wider than the line has no space to wrap on (e.g. a
            // long URL or id) — hard-break it at character boundaries so its end
            // is still visible rather than clipped off the card.
            if (fm.stringWidth(word) > maxWidth) {
                if (!current.isEmpty()) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                breakLongWord(word, fm, maxWidth, out);
                continue;
            }

            String next = current.isEmpty() ? word : current + " " + word;

            if (fm.stringWidth(next) <= maxWidth) {
                current.setLength(0);
                current.append(next);
            } else {
                if (!current.isEmpty()) {
                    out.add(current.toString());
                }

                current.setLength(0);
                current.append(word);
            }
        }

        if (!current.isEmpty()) {
            out.add(current.toString());
        }
    }

    private static void breakLongWord(String word, FontMetrics fm,
                                      int maxWidth, List<String> out) {
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (chunk.length() > 0 && fm.stringWidth(chunk.toString() + c) > maxWidth) {
                out.add(chunk.toString());
                chunk.setLength(0);
            }
            chunk.append(c);
        }
        if (chunk.length() > 0) {
            out.add(chunk.toString());
        }
    }

    private void paintTextLine(Graphics2D g2,
                               PaintLine line,
                               FontMetrics fm,
                               List<String> tokens) {
        String text = line.text();
        if (text == null) {
            return;
        }

        boolean[] searchMark = searchMarks(text, tokens);

        int selectedStart = selection.selectedStartForLine(line.lineIndex());
        int selectedEnd = selection.selectedEndForLine(line.lineIndex(),
                                                       text.length());

        selectedStart = selectedStart < 0 ? -1 : clamp(selectedStart, 0, text.length());
        selectedEnd = selectedEnd < 0 ? -1 : clamp(selectedEnd, 0, text.length());

        int pos = 0;

        while (pos < text.length()) {
            int start = pos;
            boolean highlighted = searchMark[pos];
            boolean selected = selectedStart >= 0
                    && selectedEnd >= 0
                    && pos >= selectedStart
                    && pos < selectedEnd;

            while (pos < text.length()
                    && searchMark[pos] == highlighted
                    && ((selectedStart >= 0
                         && selectedEnd >= 0
                         && pos >= selectedStart
                         && pos < selectedEnd) == selected)) {
                pos++;
            }

            String part = text.substring(start, pos);
            int partX = line.x() + fm.stringWidth(text.substring(0, start));
            int partW = fm.stringWidth(part);

            if (selected) {
                g2.setColor(SELECTION_BACKGROUND);
                g2.fillRect(partX, line.baseline() - fm.getAscent(),
                            partW, fm.getHeight());
                g2.setColor(SELECTION_FOREGROUND);
            } else {
                if (highlighted) {
                    g2.setColor(SEARCH_HIGHLIGHT);
                    g2.fillRect(partX, line.baseline() - fm.getAscent(),
                                partW, fm.getHeight());
                }
                g2.setColor(getForeground());
            }

            g2.drawString(part, partX, line.baseline());
        }
    }

    private boolean[] searchMarks(String text, List<String> tokens) {
        boolean[] mark = new boolean[text.length()];
        String lower = text.toLowerCase();

        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }

            String tok = token.toLowerCase();
            int idx = 0;

            while ((idx = lower.indexOf(tok, idx)) >= 0) {
                for (int i = idx; i < idx + tok.length() && i < mark.length; i++) {
                    mark[i] = true;
                }

                idx += Math.max(1, tok.length());
            }
        }

        return mark;
    }

    private TextPosition positionAt(Point p) {
        List<PaintLine> lines = computeLayout(getWidth());

        if (lines.isEmpty()) {
            return new TextPosition(0, 0);
        }

        PaintLine best = lines.get(0);

        for (PaintLine line : lines) {
            if (p.y >= line.top() && p.y <= line.bottom()) {
                best = line;
                break;
            }

            if (Math.abs(p.y - line.baseline())
                    < Math.abs(p.y - best.baseline())) {
                best = line;
            }
        }

        return new TextPosition(best.lineIndex(),
                                offsetForX(best.text(), p.x, best.x()));
    }

    private int offsetForX(String text, int mouseX, int textX) {
        FontMetrics fm = getFontMetrics(valueFont());

        if (mouseX <= textX) {
            return 0;
        }

        for (int i = 0; i < text.length(); i++) {
            int mid = textX
                    + fm.stringWidth(text.substring(0, i))
                    + fm.charWidth(text.charAt(i)) / 2;

            if (mouseX < mid) {
                return i;
            }
        }

        return text.length();
    }

    private String prefix(Row row) {
        return row.fieldName() == null || row.fieldName().isBlank()
                ? ""
                : row.fieldName() + ":";
    }

    private boolean matches(Object value, List<String> tokens) {
        if (value == null) return false;

        String s = String.valueOf(value).toLowerCase();

        for (String tok : tokens) {
            if (!s.contains(tok.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private boolean samePath(List<String> a, List<String> b) {
        return Objects.equals(a, b);
    }

    private static List<String> copyPath(List<String> path) {
        return path == null ? List.of() : new ArrayList<>(path);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
