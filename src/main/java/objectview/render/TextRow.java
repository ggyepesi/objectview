package objectview.render;

import objectview.field.FieldProperties;
import objectview.text.TextSelectable;
import objectview.text.TextSelection;
import objectview.text.TextSelectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class TextRow extends JComponent implements TextSelectable {

    private static final Logger log = LoggerFactory.getLogger(TextRow.class);

    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final int GAP = 8;
    private static final int MAX_PREF_WIDTH = 420;

    private static final Color SEARCH_HIGHLIGHT =
            new Color(255, 245, 120);
    private static final Color SELECTION_BACKGROUND =
            new Color(80, 140, 255);
    private static final Color SELECTION_FOREGROUND =
            Color.WHITE;

    private record PaintLine(String text,
                             int x,
                             int baseline,
                             int top,
                             int bottom,
                             int lineIndex) {
    }

    private record TextPosition(int lineIndex, int offset) {
    }

    private final String fieldName;
    private final List<String> fieldPath;
    private final List<String> lines;
    private List<String> highlightTokens = List.of();

    private final TextSelection selection =
            new TextSelection();

    private int cachedWidth = -1;
    private List<PaintLine> cachedPaintLines = new ArrayList<>();

    public TextRow(String fieldName,
                   List<String> fieldPath,
                   Object rawValue) {
        this(fieldName, fieldPath, rawValue == null
                ? List.of()
                : List.of(String.valueOf(rawValue)));
    }

    public TextRow(String fieldName,
                   List<String> fieldPath,
                   List<String> lines) {
        Card.RenderStats.textRows++;

        this.fieldName = fieldName == null ? "" : fieldName;
        this.fieldPath = fieldPath == null
                ? List.of()
                : new ArrayList<>(fieldPath);
        this.lines = lines == null ? List.of() : new ArrayList<>(lines);

        setOpaque(false);
        setFocusable(true);

        putClientProperty(FieldProperties.FIELD_NAME_PROPERTY, this.fieldName);
        putClientProperty(FieldProperties.FIELD_PATH_PROPERTY, this.fieldPath);
        putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY,
                          String.join(" ", this.lines));

        addMouseListener(TextCopyMouseHandler.INSTANCE);
        addMouseMotionListener(TextCopyMouseHandler.INSTANCE);
        setToolTipText("Drag to select · " + copyKeyName() + "/right-click to copy");

        registerCopyShortcut();
    }

    protected Color valueColor() {
        return getForeground();
    }

    protected boolean underlineValue() {
        return false;
    }

    protected int underlineThickness() {
        return 1;
    }

    protected void valueClicked(MouseEvent e) {
    }

    protected void addExtraCopyMenuItems(JPopupMenu menu) {
    }

    /** Width reserved at the far left for a leading glyph (e.g. an expand
     *  triangle); 0 by default. Subclasses that draw one override this AND
     *  {@link #paintLeadingGlyph}. */
    protected int leadingGlyphWidth() {
        return 0;
    }

    /** Paint a leading glyph at {@code x} (the left pad), aligned to the first
     *  line's {@code baseline} / {@code ascent}. No-op by default. */
    protected void paintLeadingGlyph(Graphics2D g2, int x, int baseline, int ascent) {
    }

    protected void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(text == null ? "" : text),
                            null);
    }

    private static String copyKeyName() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                == java.awt.event.InputEvent.META_DOWN_MASK
                ? "Cmd+C" : "Ctrl+C";
    }

    private void registerCopyShortcut() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        getInputMap(WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), "copy");

        getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sel = selectedText();
                copyToClipboard(sel.isBlank() ? valueText() : sel);
            }
        });
    }

    public void beginSelection(Point p) {
        TextSelectionManager.activate(this);

        pressPoint = p;
        requestFocusInWindow();
        TextPosition pos = positionAt(p, computePaintLines(getWidth()));
        selection.setAnchor(pos.lineIndex(), pos.offset());
        repaint();
    }

    // The press point, so a near-stationary release counts as a CLICK even if it
    // lands on a different character offset (a hand-click wobbles a pixel or two;
    // without this it was mistaken for a text-selection drag and the click — e.g. a
    // reference navigation — silently did nothing).
    private Point pressPoint;
    private static final int CLICK_SLOP = 4;   // px

    public void updateSelection(Point p) {
        if (!selection.hasAnchor()) {
            beginSelection(p);
            return;
        }

        TextPosition pos = positionAt(p, computePaintLines(getWidth()));
        selection.setFocus(pos.lineIndex(), pos.offset());
        repaint();
    }

    public void endSelection(Point p) {
        updateSelection(p);

        boolean nearStationary = pressPoint != null && p.distance(pressPoint) <= CLICK_SLOP;
        boolean click = selection.isEmpty() || nearStationary;
        if (NAV_DEBUG) {
            log.debug("[click] end on '" + valueText() + "' empty="
                    + selection.isEmpty() + " moved="
                    + (pressPoint == null ? "?" : (int) p.distance(pressPoint))
                    + (click ? " -> valueClicked" : " -> text SELECTION (no click)"));
        }
        pressPoint = null;
        if (click) {
            selectAll();
            valueClicked(null);
        }
    }

    static final boolean NAV_DEBUG = Boolean.getBoolean("quiz.nav.debug");

    private void selectAll() {
        List<PaintLine> lines = computePaintLines(getWidth());
        if (lines.isEmpty()) {
            return;
        }

        PaintLine last = lines.get(lines.size() - 1);
        selection.setAnchor(0, 0);
        selection.setFocus(last.lineIndex(), last.text().length());
        repaint();
    }

    public void showCopyPopup(Point p) {
        JPopupMenu menu = new JPopupMenu();
        TextSelectionManager.activate(this);

        String selectedText = selectedText();

        if (!selectedText.isBlank()) {
            JMenuItem copySelection = new JMenuItem("Copy selection");
            copySelection.addActionListener(e -> copyToClipboard(selectedText));
            menu.add(copySelection);
            menu.addSeparator();
        }

        JMenuItem copyRow = new JMenuItem("Copy row");
        copyRow.addActionListener(e -> copyToClipboard(fullText()));

        JMenuItem copyValue = new JMenuItem("Copy value");
        copyValue.addActionListener(e -> copyToClipboard(valueText()));

        menu.add(copyRow);
        menu.add(copyValue);

        if (!fieldName.isBlank()) {
            JMenuItem copyField = new JMenuItem("Copy field name");
            copyField.addActionListener(e -> copyToClipboard(fieldName));

            JMenuItem copyPath = new JMenuItem("Copy field path");
            copyPath.addActionListener(e -> copyToClipboard(pathText()));

            menu.addSeparator();
            menu.add(copyField);
            menu.add(copyPath);
        }

        addExtraCopyMenuItems(menu);

        menu.show(this, p.x, p.y);
    }

    private String selectedText() {
        if (selection.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (PaintLine line : computePaintLines(getWidth())) {
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

    private String fullText() {
        String value = valueText();
        return fieldName.isBlank() ? value : fieldName + ": " + value;
    }

    private String valueText() {
        return String.join("\n", lines);
    }

    private String pathText() {
        return String.join(".", fieldPath);
    }

    private List<String> wrappedLines(FontMetrics fm, int valueWidth) {
        List<String> out = new ArrayList<>();

        for (String line : lines) {
            wrapOneLine(line, fm, valueWidth, out);
        }

        return out.isEmpty() ? List.of("") : out;
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

    public void setHighlightTokens(List<String> tokens) {
        highlightTokens = tokens == null ? List.of() : new ArrayList<>(tokens);
        repaint();
    }

    public void clearHighlight() {
        highlightTokens = List.of();
        repaint();
    }

    private Font fieldFont() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return base.deriveFont(Font.BOLD);
    }

    private Font valueFont() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return base;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fmField = getFontMetrics(fieldFont());
        FontMetrics fmValue = getFontMetrics(valueFont());

        String prefix = fieldName.isEmpty() ? "" : fieldName + ":";
        int prefixWidth = fmField.stringWidth(prefix);
        int lead = leadingGlyphWidth();

        int longest = 0;
        for (String line : lines) {
            longest = Math.max(longest, fmValue.stringWidth(line));
        }

        int naturalWidth = PAD_X
                + lead
                + prefixWidth
                + (prefix.isEmpty() ? 0 : GAP)
                + longest
                + PAD_X;

        int prefWidth = Math.max(160, Math.min(MAX_PREF_WIDTH, naturalWidth));

        int layoutWidth = getWidth() > 0 ? getWidth() : prefWidth;
        int valueWidth = Math.max(
                80,
                layoutWidth - PAD_X - lead - prefixWidth - GAP - PAD_X);

        List<String> wrapped = wrappedLines(fmValue, valueWidth);
        int height = PAD_Y * 2 + wrapped.size() * fmValue.getHeight();

        return new Dimension(prefWidth, height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);

        if (widthChanged) {
            cachedWidth = -1;
            cachedPaintLines = new ArrayList<>();
            revalidate();
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(80, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        try {
            Font fieldFont = fieldFont();
            Font valueFont = valueFont();

            FontMetrics fmField = g2.getFontMetrics(fieldFont);
            FontMetrics fmValue = g2.getFontMetrics(valueFont);

            String prefix = fieldName.isEmpty() ? "" : fieldName + ":";

            int lead = leadingGlyphWidth();
            int firstBaseline = PAD_Y + fmValue.getAscent();

            if (lead > 0) {
                paintLeadingGlyph(g2, PAD_X, firstBaseline, fmValue.getAscent());
            }

            int x = PAD_X + lead;

            if (!prefix.isEmpty()) {
                g2.setFont(fieldFont);
                g2.setColor(getForeground());
                g2.drawString(prefix, x, firstBaseline);
            }

            g2.setFont(valueFont);

            for (PaintLine line : computePaintLines(getWidth())) {
                paintTextLine(g2,
                              line.text(),
                              line.x(),
                              line.baseline(),
                              fmValue,
                              line.lineIndex());
            }
        } finally {
            g2.dispose();
        }
    }

    private List<PaintLine> computePaintLines(int width) {
        if (width == cachedWidth && !cachedPaintLines.isEmpty()) {
            return cachedPaintLines;
        }

        FontMetrics fmField = getFontMetrics(fieldFont());
        FontMetrics fmValue = getFontMetrics(valueFont());

        String prefix = fieldName.isEmpty() ? "" : fieldName + ":";
        int prefixWidth = fmField.stringWidth(prefix);
        int valueX = PAD_X + leadingGlyphWidth()
                + (prefix.isEmpty() ? 0 : prefixWidth + GAP);
        int valueWidth = Math.max(80, width - valueX - PAD_X);

        List<String> wrapped = wrappedLines(fmValue, valueWidth);
        List<PaintLine> out = new ArrayList<>();

        int y = PAD_Y + fmValue.getAscent();

        for (int i = 0; i < wrapped.size(); i++) {
            String line = wrapped.get(i);
            out.add(new PaintLine(line, valueX, y,
                                  y - fmValue.getAscent(),
                                  y + fmValue.getDescent(),
                                  i));
            y += fmValue.getHeight();
        }

        cachedWidth = width;
        cachedPaintLines = out;
        return out;
    }

    private void paintTextLine(Graphics2D g2,
                               String text,
                               int x,
                               int baseline,
                               FontMetrics fm,
                               int lineIndex) {
        if (text == null || text.isEmpty()) {
            return;
        }

        boolean[] searchMark = searchMarks(text);

        int selectedStart = selection.selectedStartForLine(lineIndex);
        int selectedEnd = selection.selectedEndForLine(lineIndex, text.length());

        selectedStart = selectedStart < 0
                ? -1
                : clamp(selectedStart, 0, text.length());
        selectedEnd = selectedEnd < 0
                ? -1
                : clamp(selectedEnd, 0, text.length());

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
            int partX = x + fm.stringWidth(text.substring(0, start));
            int partW = fm.stringWidth(part);

            if (selected) {
                g2.setColor(SELECTION_BACKGROUND);
                g2.fillRect(partX,
                            baseline - fm.getAscent(),
                            partW,
                            fm.getHeight());
                g2.setColor(SELECTION_FOREGROUND);
            } else {
                if (highlighted) {
                    g2.setColor(SEARCH_HIGHLIGHT);
                    g2.fillRect(partX,
                                baseline - fm.getAscent(),
                                partW,
                                fm.getHeight());
                }
                g2.setColor(valueColor());
            }

            g2.drawString(part, partX, baseline);

            if (!selected && underlineValue()) {
                int underY = baseline + 1;
                g2.fillRect(partX, underY, partW, underlineThickness());
            }
        }
    }

    private boolean[] searchMarks(String text) {
        boolean[] mark = new boolean[text.length()];
        String lower = text.toLowerCase();

        // Highlight only in text that itself contains ALL the query tokens — so a
        // token doesn't light up in an unrelated row (searching "qualifier lo" must
        // NOT mark "qualifier" in a "qualifier value" row that lacks "lo"). This
        // matches the token-AND search semantics, at the row level.
        for (String token : highlightTokens) {
            if (token != null && !token.isBlank()
                    && !lower.contains(token.toLowerCase())) {
                return mark;   // a required token is absent here → highlight nothing
            }
        }

        for (String token : highlightTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }

            String tok = token.toLowerCase();
            int idx = 0;

            while ((idx = lower.indexOf(tok, idx)) >= 0) {
                for (int i = idx;
                     i < idx + tok.length() && i < mark.length;
                     i++) {
                    mark[i] = true;
                }

                idx += Math.max(1, tok.length());
            }
        }

        return mark;
    }

    private TextPosition positionAt(Point p, List<PaintLine> paintLines) {
        if (paintLines.isEmpty()) {
            return new TextPosition(0, 0);
        }

        PaintLine best = paintLines.get(0);

        for (PaintLine line : paintLines) {
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

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void clearSelectionFromManager() {
        selection.clear();
        repaint();
    }

    @Override
    public boolean hasActiveSelection() {
        return !selection.isEmpty();
    }
}