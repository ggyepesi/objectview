package objectview.render;

import objectview.*;
import objectview.annotations.Inline;
import objectview.annotations.Reference;
import objectview.demo.CardFrame;
import objectview.media.ImageBlurrer;
import objectview.media.ImagePane;
import objectview.media.MediaValue;
import objectview.field.FieldKind;
import objectview.field.FieldProperties;
import objectview.viewconfig.ViewConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import objectview.utils.swing.GridBagUtils;
import objectview.annotations.Link;
import objectview.field.DynamicFields;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

/**
 * Renders a {@link Viewable} as a card by reflecting over its fields.
 *
 * <h3>Field annotations (rendering hints)</h3>
 * <ul>
 *   <li><b>(none)</b> — scalar leaves (String/number/enum) fold into a
 *       shared, drag-selectable {@link TextBlock}. A nested
 *       {@link Viewable} value — whether a single field or a member of a
 *       collection/map, at any depth — renders as a <i>collapsed
 *       reference chip</i>.</li>
 *   <li>{@link Inline @Inline} — force the
 *       nested Viewable(s) to render fully expanded inline (recursively). Use
 *       only on small, bounded structures (e.g. a log tree); never on
 *       broad/cyclic graphs.</li>
 *   <li>{@link Reference @Reference} — explicit chip;
 *       an intent-marking alias of the default. Kept for clarity and for
 *       fields that must never be force-inlined.</li>
 *   <li>{@link Link @Link} — a String URL field; rendered as a
 *       clickable link row (see {@link LinkRow}).</li>
 *   <li>{@code @Hidden} — not rendered. {@code @Minor} —
 *       hidden unless the config opts minor fields in.</li>
 * </ul>
 *
 * <h3>Reference UI behaviour</h3>
 * A reference chip shows a ▶/▼ triangle. Left-click toggles
 * <i>expand/collapse in place</i>: expanding flips per-target state in
 * {@link RenderContext} and rebuilds the card via {@link
 * #refresh()}, rendering the chip plus an inline panel below it. The
 * inline panel's own references are themselves collapsed chips, so each
 * click opens exactly one level — bounded and safe even for large graphs.
 * Shift- or double-click opens the target in its own detail window.
 *
 * <h3>Copy</h3>
 * Painted text rows/blocks support drag-select (or click to select all),
 * {@code Cmd/Ctrl+C}, and a right-click copy menu; chips and link rows
 * offer right-click copy.
 *
 * <h3>Components</h3>
 * Text is drawn by lightweight painted components ({@link TextRow},
 * {@link TextBlock}, {@link ReferenceRow}, {@link
 * LinkRow}) rather than per-value Swing widgets, so a card with
 * tens of thousands of fields stays cheap. The only structural extra is a
 * single top-pinning {@link Box.Filler} per root card.
 */
public class Card extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(Card.class);

    // A complex collection/map field renders under a collapsible header,
    // collapsed by default (threshold 0 => no list auto-expands); click the
    // header to expand. Toggleable per collection.
    private static final int COLLECTION_COLLAPSE_THRESHOLD = 0;

    private final Viewable viewable;
    private final ViewConfig config;
    private final boolean fill;

    private Color highlightColor = null;

    // Minimum on-screen footprint for this card, enforced as a floor rather
    // than a frozen preferred size: the card still grows naturally when a
    // reference chip is expanded in place (otherwise GridBag would compress
    // the extra content into the old height, collapsing the image and
    // hiding rows — and the scroll pane couldn't reach the grown top).
    private Dimension cardSizeFloor = null;

    // Cached result of the (expensive) super.getPreferredSize() — measuring a card
    // walks all its rows (FontMetrics + wrapping). The parent's GridBagLayout calls
    // getPreferredSize() on EVERY card whenever ANYTHING revalidates (e.g. one card
    // expands), so without this a re-layout re-measures all ~22k cards = freeze.
    // Cleared on invalidate(), which Swing fires when this card's own content/size
    // actually changes — so only the changed card re-measures; the rest return the
    // cached size. Width-dependent height self-corrects: a width change resizes the
    // card, which invalidates it, dropping the cache.
    private Dimension cachedPreferred = null;

    @Override
    public void invalidate() {
        cachedPreferred = null;
        super.invalidate();
    }

    public void setCardSizeFloor(Dimension floor) {
        this.cardSizeFloor = floor;
        if (floor != null) {
            setMinimumSize(new Dimension(
                    Math.min(floor.width, 220), Math.min(floor.height, 220)));
        }
        revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = cachedPreferred;
        if (d == null) {
            d = new Dimension(super.getPreferredSize());
            cachedPreferred = d;
        }
        if (cardSizeFloor != null) {
            return new Dimension(
                    Math.max(d.width, cardSizeFloor.width),
                    Math.max(d.height, cardSizeFloor.height));
        }
        return new Dimension(d);
    }

    public void setHighlightColor(Color c) {
        this.highlightColor = c;
        repaint();
    }

    private static final Color SELECTION_TINT = new Color(30, 110, 210, 28);
    private static final Color SELECTION_BORDER = new Color(30, 110, 210);

    @Override
    protected void paintComponent(Graphics g) {
        boolean selected = renderContext != null && renderContext.isSelected(viewable);
        if (highlightColor != null) {
            g.setColor(highlightColor);
            g.fillRect(0, 0, getWidth(), getHeight());
        } else if (selected) {
            g.setColor(SELECTION_TINT);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        super.paintComponent(g);
        // A repaint-only selection ring drawn just inside the card border, so
        // toggling selection never changes the card's measured size (which would
        // force the virtualized list to re-measure every card).
        if (selected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(SELECTION_BORDER);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
            g2.dispose();
        }
    }

    private final Set<Object> visited;
    private final Set<Object> ancestors;
    private final RenderContext renderContext;
    private boolean renderedConfiguredContent = false;

    private final List<String> path;
    private int firstFieldRow = 0;

    // When true, this panel skips its own title header because the name is
    // already shown immediately above it (the reference chip that expanded
    // into it, or a wrapper whose displayName is this object's name). Avoids
    // echoing the same name two/three times down a card. See addRenderedField
    // and collapsibleReference.
    private boolean suppressTitle = false;

    public static <T> Set<T> identitySetOf() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public Card(Viewable viewable,
                ViewConfig config,
                boolean fill) {
        this(identitySetOf(), identitySetOf(), new RenderContext(),
                true, viewable, config, fill, new ArrayList<>(), null, null);
    }

    // Root render whose own title is suppressed -- e.g. an "Open in window"
    // frame already shows the name in its title bar.
    public Card(Viewable viewable,
                ViewConfig config,
                boolean fill,
                boolean suppressTitle) {
        this(identitySetOf(), identitySetOf(), new RenderContext(),
                true, viewable, config, fill, new ArrayList<>(), null, null, suppressTitle);
    }

    public Card(Viewable viewable,
                ViewConfig config,
                Collection<? extends Viewable> topLevel,
                boolean fill) {
        this(identitySetOf(), identitySetOf(), new RenderContext(topLevel),
                true, viewable, config, fill, new ArrayList<>(), null, null);
    }

    public Card(Viewable viewable,
                ViewConfig config,
                RenderContext renderContext,
                boolean fill) {
        this(identitySetOf(), identitySetOf(), renderContext,
                true, viewable, config, fill, new ArrayList<>(),
             null, null);
    }

    public Card(Set<Object> visited,
                Set<Object> ancestors,
                RenderContext renderContext,
                boolean rootRender,
                Viewable viewable,
                ViewConfig config,
                boolean fill,
                List<String> path) {
        this(visited, ancestors, renderContext, rootRender,
                viewable, config, fill, path, null, null);
    }

    public Card(Viewable viewable,
                ViewConfig config,
                boolean fill,
                JComponent compiledView) {
        this(identitySetOf(), identitySetOf(), new RenderContext(),
                true, viewable, config, fill, new ArrayList<>(),
             null, compiledView);
    }


    /**
     * Shouldn't be static! If static then
     * Arguments can't fit into locals in class file quiz/ui/Card$RenderStats
     */
    public final class RenderStats {
        public static final Map<String, Integer> panels = new TreeMap<>();
        public static int textRows = 0;
        public static int textBlocks = 0;
        public static int referenceRows = 0;

        public static void panel(Object q) {
            if (q != null) {
                panels.merge(q.getClass().getSimpleName(), 1, Integer::sum);
            }
        }

        public static void print() {
            log.debug("TextRows=" + textRows);
            log.debug("TextBlocks=" + textBlocks);
            log.debug("ReferenceRows=" + referenceRows);
            log.debug("Panels=" + panels);
        }
    }

    public Card(Set<Object> visited,
                Set<Object> ancestors,
                RenderContext renderContext,
                boolean rootRender,
                Viewable viewable,
                ViewConfig config,
                boolean fill,
                List<String> path,
                List<Viewable> objectPath,
                JComponent compiledView) {
        this(visited, ancestors, renderContext, rootRender, viewable, config,
                fill, path, objectPath, compiledView, false);
    }

    public Card(Set<Object> visited,
                Set<Object> ancestors,
                RenderContext renderContext,
                boolean rootRender,
                Viewable viewable,
                ViewConfig config,
                boolean fill,
                List<String> path,
                List<Viewable> objectPath,
                JComponent compiledView,
                boolean suppressTitle) {
        this.suppressTitle = suppressTitle;
        RenderStats.panel(viewable);
        // addMouseListener(new DeepComponentInspector());

        List<Viewable> objectPath1 = objectPath == null
                ? new ArrayList<>()
                : new ArrayList<>(objectPath);

        if (rootRender && viewable != null && objectPath1.isEmpty()) {
            objectPath1.add(viewable);
        }

        this.viewable = viewable;
        this.visited = visited == null ? identitySetOf() : visited;
        this.ancestors = ancestors == null ? identitySetOf() : ancestors;
        this.renderContext = renderContext == null
                ? new RenderContext()
                : renderContext;
        this.fill = fill;
        this.path = path == null ? new ArrayList<>() : new ArrayList<>(path);

        this.config = config == null
                ? ViewConfig.of(viewable == null ? null : viewable.getClass())
                : config;

        setLayout(new GridBagLayout());
        setOpaque(false);

        if (viewable == null) {
            return;
        }

        if (compiledView != null) {
            this.visited.add(viewable);
            setLayout(new BorderLayout());
            add(compiledView, BorderLayout.CENTER);
            renderedConfiguredContent = true;
            return;
        }

        if (rootRender) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)
            ));
        }

        if (!rootRender) {
            assert ancestors != null;
            if (ancestors.contains(viewable)) {
                addCompactReference(viewable, false);
                return;
            }
        }

        if (!rootRender) {
            assert visited != null;
            if (visited.contains(viewable)) {
                addCompactReference(viewable, false);
                return;
            }
        }

        if (!rootRender && this.renderContext.isTopLevel(viewable)) {
            addCompactReference(viewable, true);
            return;
        }

        this.visited.add(viewable);
        this.ancestors.add(viewable);

        if (rootRender && this.renderContext.collapsibleCards()) {
            buildCollapsibleRoot();
        } else {
            addTitleHeaderIfNeeded();
            buildFields();
            ensureTitleHasRoom();
        }
        this.ancestors.remove(viewable);
    }

    public boolean hasRenderedConfiguredContent() {
        return renderedConfiguredContent;
    }

    /**
     * Rebuilds this card's content in place from the (possibly mutated)
     * backing viewable, keeping the same panel instance so any attached
     * search/sort/scroll/highlight state stays bound to the same card.
     *
     * Targets the standard field-rendered card. Compiled-view cards (which
     * use a BorderLayout wrapper) are left untouched, since their content
     * is an externally supplied component rather than reflected fields.
     * Call on the Event Dispatch Thread.
     */
    public void refresh() {
        if (viewable == null || !(getLayout() instanceof GridBagLayout)) {
            return;
        }

        removeAll();

        firstFieldRow = 0;
        renderedConfiguredContent = false;

        // visited/ancestors are this card's own cycle-detection sets;
        // reset them so the rebuild re-renders nested references that the
        // first pass had already marked as seen.
        visited.clear();
        ancestors.clear();

        visited.add(viewable);
        ancestors.add(viewable);

        if (renderContext.collapsibleCards()) {
            buildCollapsibleRoot();
        } else {
            addTitleHeaderIfNeeded();
            buildFields();
            ensureTitleHasRoom();
        }

        ancestors.remove(viewable);

        revalidate();
        repaint();
    }

    private void addTitleHeaderIfNeeded() {
        String title = getTitle();

        if (title == null || title.isEmpty() || suppressTitle || wrapsSameNameChild()) {
            firstFieldRow = 0;
            return;
        }

        renderedConfiguredContent = true;

        add(createTitleHeader(viewable),
                GridBagUtils.gbc(
                        0, 0,
                        1.0, 0.0,
                        GridBagConstraints.NORTHWEST,
                        GridBagConstraints.HORIZONTAL,
                        new Insets(2, 2, 4, 2)));

        firstFieldRow = 1;
    }

    // A birdseye root card: a name header with an expand/collapse triangle,
    // collapsed by default. Expanding shows the full fields below. The triangle
    // flips the per-card state in the render context, then asks the view to
    // rebuild THIS card fresh (factory-driven), so its new size is re-measured
    // rather than grown in place. Selection (name click) coexists with the toggle.
    private void buildCollapsibleRoot() {
        boolean expanded = renderContext.isCardExpanded(viewable, false);

        add(collapsibleRootHeader(expanded),
                GridBagUtils.gbc(
                        0, 0,
                        1.0, 0.0,
                        GridBagConstraints.NORTHWEST,
                        GridBagConstraints.HORIZONTAL,
                        new Insets(2, 2, expanded ? 4 : 2, 2)));
        renderedConfiguredContent = true;

        if (expanded) {
            firstFieldRow = 1;
            buildFields();
        }
    }

    private JComponent collapsibleRootHeader(boolean expanded) {
        String title = safeName(viewable);
        if (title.isEmpty()) {
            title = String.valueOf(viewable);
        }

        JLabel toggle = new JLabel(expanded ? "▼ " : "▶ ");
        toggle.setForeground(new Color(0, 80, 180));
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setToolTipText(expanded ? "Collapse" : "Expand");
        toggle.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                e.consume();
                renderContext.toggleCardExpanded(viewable, false);
                renderContext.notifyCardToggled(viewable);
            }
        });

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0, 80, 180));
        titleLabel.putClientProperty(FieldProperties.FIELD_NAME_PROPERTY, "name");
        titleLabel.putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, title);
        titleLabel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY, List.of("name"));

        if (renderContext.selectionEnabled()) {
            titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            titleLabel.setToolTipText("Click to select");
            titleLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 1) {
                        renderContext.select(viewable);
                    }
                }
            });
        } else if (config.isAddListener()) {
            addOpenListener(titleLabel, viewable);
        }

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        header.add(toggle, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent createTitleHeader(Viewable q) {
        return createTitleHeader(q, false);
    }

    private JComponent createTitleHeader(Viewable q, boolean focusTopLevel) {
        String title = safeName(q);

        if (title.isEmpty()) {
            title = String.valueOf(q);
        }

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0, 80, 180));
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titleLabel.setToolTipText(
                focusTopLevel
                        ? "Click to focus existing panel"
                        : "Double-click to open full view");

        titleLabel.putClientProperty(FieldProperties.FIELD_NAME_PROPERTY, "name");
        titleLabel.putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, title);
        titleLabel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY, List.of("name"));

        // A view can enable single-selection (e.g. curation, to pick the instance
        // to fill): a single click on the card's name selects it — the render
        // context tracks the one selected object, repaints the affected cards, and
        // notifies listeners. Double-click still opens the detail view below.
        if (renderContext != null && renderContext.selectionEnabled()) {
            titleLabel.setToolTipText("Click to select — double-click to open");
            titleLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 1) {
                        renderContext.select(viewable);
                    }
                }
            });
        }

        if (config.isAddListener()) {
            if (focusTopLevel) {
                addFocusTopLevelListener(titleLabel, q);
            } else {
                addOpenListener(titleLabel, q);
            }
        }

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createMatteBorder(
                0, 0, 1, 0, Color.LIGHT_GRAY));
        header.add(titleLabel, BorderLayout.WEST);

        return header;
    }

    private void addCompactReference(Viewable q, boolean focusTopLevel) {
        ViewConfig openCfg = configForNested(q);

        addSingle(
                new ReferenceRow(
                        "",
                        namePath(path),
                        q,
                        renderContext,
                        openCfg,
                        objectPathTitle(q),
                        false),
                0);

        setMinimumSize(new Dimension(100, 42));
    }

    private String objectPathTitle(Viewable target) {
        List<String> names = new ArrayList<>();

        if (viewable != null
                && viewable.getName() != null
                && !viewable.getName().isBlank()) {
            names.add(viewable.getName());
        }

        if (target != null
                && target.getName() != null
                && !target.getName().isBlank()) {
            names.add(target.getName());
        }

        return String.join(" → ", names);
    }

    // Renders the entity's MEDIA field(s) — a portrait / flag (ImagePane or MediaValue)
    // — at the top of the card (the header thumbnail / avatar), recording their names so
    // the normal field pass skips them. Reflection-declared fields only; dynamic
    // (map-backed) media stays inline for now.
    private int appendHoistedMedia(int row, java.util.Set<String> hoisted) {
        for (Field field : config.visibleFieldsFor(viewable.getClass())) {
            String name = field.getName();
            if ("name".equals(name)) {
                continue;
            }
            Object value;
            try {
                value = field.get(viewable);
            } catch (Exception e) {
                continue;
            }
            if (value == null || FieldKind.ofValue(value) != FieldKind.MEDIA) {
                continue;
            }
            row = addRenderedField(field, value, row);
            hoisted.add(name);
        }
        return row;
    }

    private void buildFields() {
        int row = firstFieldRow;

        List<TextBlock.Row> textRows = new ArrayList<>();

        // Hoist a MEDIA field (a portrait / flag — ImagePane or MediaValue) to the TOP
        // of the card so the entity's image reads as its avatar instead of sitting
        // buried among the text fields; the hoisted field is skipped in the pass below.
        java.util.Set<String> hoistedMedia = new java.util.HashSet<>();
        row = appendHoistedMedia(row, hoistedMedia);

        for (Field field : config.visibleFieldsFor(viewable.getClass())) {
            String name = field.getName();

            if ("name".equals(name) || hoistedMedia.contains(name)) {
                continue;
            }

            Object value;

            try {
                value = field.get(viewable);
            } catch (Exception e) {
                continue;
            }

            // A field that renders nothing must not break the text-block
            // batch: a null/empty leaf (e.g. a blank error) sitting between
            // two value leaves would otherwise split them into separate
            // blocks and open a stray, variable vertical gap.
            if (value == null || isEmptyCollectionOrMap(value)) {
                continue;
            }

            // A DynamicFields object's map IS its field set — render each entry as
            // its own field (flat), never the map as one "dynamicFields" block.
            if (viewable instanceof DynamicFields df
                    && value == df.dynamicFieldValues()) {
                for (Map.Entry<String, Object> entry
                        : df.dynamicFieldValues().entrySet()) {
                    row = appendDynamicEntry(entry.getKey(), entry.getValue(), textRows, row);
                }
                continue;
            }

            List<String> fieldPath = new ArrayList<>(path);
            fieldPath.add(name);

            // A boolean flag reads as a badge, not "won: true": render nothing
            // when false, and just the humanized field name when true.
            if (value instanceof Boolean flag) {
                if (flag) {
                    textRows.add(new TextBlock.Row(
                            null, fieldPath, value,
                            List.of(FieldLabels.humanize(name))));
                }
                continue;
            }

            if (isTextBlockCandidate(field, value)) {
                textRows.add(textBlockRow(name, fieldPath, value));
                continue;
            }

            if (!textRows.isEmpty()) {
                row = addTextBlock(textRows, row);
                textRows.clear();
            }

            row = addRenderedField(field, value, row);
        }

        if (!textRows.isEmpty()) {
            row = addTextBlock(textRows, row);
        }

        // Root cards only: pin fields to the top by absorbing any extra
        // card height in one zero-paint filler, instead of letting GridBag
        // centre the content (which left a variable gap). Nested panels are
        // content-sized, so they don't need it.
        if (path.isEmpty()) {
            add(Box.createGlue(), GridBagUtils.gbc(
                    0, row + 1, 1.0, 1.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 0)));
        }
    }

    private int addTextBlock(List<TextBlock.Row> rows, int row) {
        TextBlock block = new TextBlock(rows);

        if (!block.isEmpty()) {
            addSingle(block, row++);
        }

        return row;
    }

    // Renders one entry of a DynamicFields map as a field — scalars fold into the
    // shared text block, complex values (references, images, collections) render
    // standalone — the same treatment declared fields get, minus reflection.
    private int appendDynamicEntry(String key, Object value,
                                   List<TextBlock.Row> textRows, int row) {
        if (value == null || isEmptyCollectionOrMap(value) || "name".equals(key)) {
            return row;
        }
        List<String> fieldPath = new ArrayList<>(path);
        fieldPath.add(key);

        if (value instanceof Boolean flag) {
            if (flag) {
                textRows.add(new TextBlock.Row(null, fieldPath, value,
                                               List.of(FieldLabels.humanize(key))));
            }
            return row;
        }

        // A single reference renders through the SAME collapsible chip a declared
        // reference gets — but seeded expanded, so it looks like today's always-inline
        // dynamic field while becoming a collapsible, toggleable chip (#87). A
        // collection of references keeps the collection renderer (whose items are
        // already collapsible chips).
        if (value instanceof Viewable q) {
            if (!textRows.isEmpty()) {
                row = addTextBlock(textRows, row);
                textRows.clear();
            }
            JComponent comp = collapsibleReference(key, fieldPath, q, true);
            if (comp != null) {
                addSingle(comp, row++);
            }
            return row;
        }

        boolean complex = value instanceof ImagePane
                || value instanceof MediaValue
                || value instanceof Collection<?> || value instanceof Map<?, ?>;
        if (!complex) {
            textRows.add(textBlockRow(key, fieldPath, value));
            return row;
        }

        if (!textRows.isEmpty()) {
            row = addTextBlock(textRows, row);
            textRows.clear();
        }
        ViewConfig cfg = config.getFieldConfig(key);
        if (cfg == null) {
            cfg = defaultConfigForValue(value);
        }
        JComponent comp = ValueRenderer.createFieldComponent(
                copyVisited(), copyAncestors(), renderContext,
                key, fieldPath, value, cfg, fill);
        if (comp != null) {
            addSingle(comp, row++);
        }
        return row;
    }

    private int addRenderedField(Field field, Object value, int row) {
        if (value == null || isEmptyCollectionOrMap(value)) {
            return row;
        }

        String fieldName = field.getName();
        List<String> fieldPath = new ArrayList<>(path);
        fieldPath.add(fieldName);

        // @Provenance (a Source) renders like @Reference: a collapsed
        // chip, never force-inlined — the annotation drives the chipping.
        //
        // The dynamic-field container map of a DynamicFields object (e.g. a raw
        // WikidataDynamicObject) must NOT be treated as one collapsible group —
        // its entries ARE the object's fields, so collapsing it hides all the
        // content behind a "dynamicFields (n)" header. Render it normally; only
        // genuine value collections/maps collapse.
        boolean isDynamicContainer =
                viewable instanceof DynamicFields df
                        && value == df.dynamicFieldValues();
        boolean isCollectionOrMap =
                (value instanceof Collection<?> || value instanceof Map<?, ?>)
                        && !isDynamicContainer;

        if (ViewableAdapter.isReference(field)
                || ViewableAdapter.isProvenanceField(field)) {
            if (isCollectionOrMap) {
                // The header labels the field; build the items borderless.
                Object v = value;
                return addCollapsibleCollection(fieldName, fieldPath, value, row,
                        () -> createReferenceFieldComponent("", fieldPath, v));
            }
            JComponent comp =
                    createReferenceFieldComponent(fieldName, fieldPath, value);

            if (comp != null) {
                addSingle(comp, row++);
            }

            return row;
        }

        // @Inline means "always render fully expanded inline" (e.g. a
        // query-log step tree) — never collapse it, or the nested content (the
        // SPARQL, child steps) hides behind a collapsed header.
        if (ViewableAdapter.isInline(field)) {
            JComponent comp =
                    createInlineFieldComponent(fieldName, fieldPath, value);

            if (comp != null) {
                addSingle(comp, row++);
            }

            return row;
        }

        if (ViewableAdapter.isLinkField(field)
                && value instanceof String url
                && !url.isBlank()) {

            Link link = field.getAnnotation(Link.class);
            String label = link == null ? "" : link.text();
            addSingle(new LinkRow(fieldName, fieldPath, url, label), row++);
            return row;
        }

        // A bare (non-annotated) single Viewable is a collapsible chip too,
        // matching collection members -- see the class doc.
        if (value instanceof Viewable q) {
            JComponent comp = collapsibleReference(fieldName, fieldPath, q);

            if (comp != null) {
                addSingle(comp, row++);
            }

            return row;
        }

        // Quiz query panels: show the answer-hiding (masked/blurred) image.
        if (value instanceof ImagePane ip && config.isBlurImages() && viewable != null) {
            value = blurForQuiz(ip);
        }

        ViewConfig fieldCfg = config.getFieldConfig(fieldName);

        if (fieldCfg == null) {
            fieldCfg = defaultConfigForValue(value);
        }

        // A complex collection/map (simple ones already folded into a text
        // block) renders under a collapsible header; build the items borderless
        // (the header carries the field name) and only when expanded.
        if (isCollectionOrMap) {
            ViewConfig cfg = fieldCfg;
            Object collValue = value;
            return addCollapsibleCollection(fieldName, fieldPath, value, row,
                    () -> ValueRenderer.createFieldComponent(
                            copyVisited(), copyAncestors(), renderContext,
                            "", fieldPath, collValue, cfg, fill));
        }

        JComponent comp = ValueRenderer.createFieldComponent(
                copyVisited(),
                copyAncestors(),
                renderContext,
                // The dynamic-field container's entries ARE the object's own fields —
                // render them flat, WITHOUT the enclosing "dynamicFields" titled border
                // (a blank name skips the border in basePanel).
                isDynamicContainer ? "" : fieldName,
                fieldPath,
                value,
                fieldCfg,
                fill);

        if (comp != null) {
            addSingle(comp, row++);
        }

        return row;
    }

    // Renders a complex collection/map field as a collapsible group: a clickable
    // "{field} (N)" header plus, when expanded, the items built by {@code body}.
    // Lists over COLLECTION_COLLAPSE_THRESHOLD start collapsed; the per-collection
    // toggle is remembered in the render context (keyed by the collection's
    // identity), and the body is built only when expanded so a collapsed long
    // list stays cheap.
    private int addCollapsibleCollection(
            String fieldName,
            List<String> fieldPath,
            Object value,
            int row,
            java.util.function.Supplier<JComponent> body) {

        int count = value instanceof Collection<?> c ? c.size()
                : value instanceof Map<?, ?> m ? m.size()
                : 0;
        if (count == 0) {
            return row;
        }

        boolean defaultExpanded = count <= COLLECTION_COLLAPSE_THRESHOLD;
        boolean expanded =
                renderContext.isCollectionExpanded(value, defaultExpanded);

        CollectionHeader header = new CollectionHeader(
                fieldName, fieldPath, count, expanded, value,
                defaultExpanded, renderContext);

        if (!expanded) {
            addSingle(header, row++);
            return row;
        }

        JComponent items = body.get();
        if (items == null) {
            addSingle(header, row++);
            return row;
        }

        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(header, GridBagUtils.gbc(
                0, 0, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 0)));
        wrap.add(items, GridBagUtils.gbc(
                0, 1, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0, 16, 2, 0)));

        addSingle(wrap, row++);
        return row;
    }

    private JComponent createReferenceFieldComponent(
            String fieldName,
            List<String> fieldPath,
            Object value
                                                    ) {
        if (value instanceof Viewable q) {
            return collapsibleReference(fieldName, fieldPath, q);
        }

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        if (fieldName != null && !fieldName.isBlank()) {
            panel.setBorder(BorderFactory.createTitledBorder(fieldName));
        }

        int row = 0;

        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Viewable q) {
                    addReferenceToPanel(panel, "", q, fieldPath, row++);
                }
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (item instanceof Viewable q) {
                    addReferenceToPanel(panel, "", q, fieldPath, row++);
                }
            }
        }

        return row == 0 ? null : panel;
    }

    // Opposite of createReferenceFieldComponent: each nested Viewable is
    // expanded fully in place rather than shown as a click-to-open chip.
    // Only reached for @Inline fields, so the broad/cyclic graphs
    // that rely on the reference default are never expanded here.
    private JComponent createInlineFieldComponent(
            String fieldName,
            List<String> fieldPath,
            Object value) {

        if (value instanceof Viewable q) {
            return inlineViewable(q, fieldPath);
        }

        Collection<?> items =
                value instanceof Collection<?> c ? c
                        : value instanceof Map<?, ?> m ? m.values()
                        : List.of();

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        if (fieldName != null && !fieldName.isBlank()) {
            panel.setBorder(BorderFactory.createTitledBorder(fieldName));
        }

        int row = 0;

        for (Object item : items) {
            if (!(item instanceof Viewable q)) {
                continue;
            }

            // Each element of an inline COLLECTION renders as its own collapsible
            // chip (▶/▼), so the titled-border list (e.g. a query log's `steps`) is a
            // scannable set of expandable items rather than one flat wall of every
            // step's content. Expand state is keyed by the target identity, so each
            // chip toggles independently. (A single inline Viewable — handled above —
            // still expands in place.)
            JComponent nested = collapsibleReference("", fieldPath, q);

            if (nested != null) {
                panel.add(
                        nested,
                        GridBagUtils.gbc(
                                0, row++,
                                1.0, 0.0,
                                GridBagConstraints.NORTHWEST,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(2, 6, 2, 6)));
            }
        }

        return row == 0 ? null : panel;
    }

    private JComponent inlineViewable(Viewable q, List<String> fieldPath) {
        return inlineViewable(q, fieldPath, false);
    }

    // suppressTitle: the name is already shown above (the chip that expanded
    // into this body, or a same-named wrapper), so don't repeat it as a title.
    private JComponent inlineViewable(Viewable q, List<String> fieldPath, boolean suppressTitle) {
        Card nested =
                new Card(
                        copyVisited(),
                        copyAncestors(),
                        renderContext,
                        false,
                        q,
                        configForNested(q),
                        fill,
                        fieldPath,
                        null,
                        null,
                        suppressTitle);

        return nested.hasRenderedConfiguredContent() ? nested : null;
    }

    private void addReferenceToPanel(
            JPanel panel,
            String fieldName,
            Viewable q,
            List<String> fieldPath,
            int row
    ) {
        panel.add(collapsibleReference(fieldName, fieldPath, q),
                GridBagUtils.gbc(
                        0, row,
                        1.0, 0.0,
                        GridBagConstraints.NORTHWEST,
                        GridBagConstraints.HORIZONTAL,
                        new Insets(2, 6, 2, 6)));
    }

    // A Viewable reference renders as a collapsed chip by default; clicking
    // it (see ReferenceRow) flips renderContext expand state and
    // rebuilds the card, so here it renders the chip plus the inline panel.
    // Children of the inline panel are themselves collapsed chips, so only
    // one level opens per click -- safe even for broad/cyclic graphs.
    private JComponent collapsibleReference(
            String fieldName,
            List<String> fieldPath,
            Viewable target) {
        return collapsibleReference(fieldName, fieldPath, target, false);
    }

    // As above, but {@code defaultExpanded} seeds the initial state when the user
    // hasn't toggled this reference yet — true for a reference that used to render
    // always-inline (a dynamic map field), so it looks the same but is now a
    // collapsible chip rather than a fixed inline panel.
    private JComponent collapsibleReference(
            String fieldName,
            List<String> fieldPath,
            Viewable target,
            boolean defaultExpanded) {

        // A reference to something that is itself a top-level card in this view
        // is a navigation link (jump to that card) rather than an expand-in-place
        // chip — so the same object never has two competing expand toggles.
        if (renderContext != null && renderContext.isTopLevel(target)) {
            return new ReferenceRow(
                    fieldName,
                    namePath(fieldPath),
                    target,
                    renderContext,
                    configForNested(target),
                    objectPathTitle(target),
                    false,
                    true);
        }

        boolean exp = renderContext != null
                && renderContext.isExpanded(target, defaultExpanded);

        ReferenceRow chip =
                new ReferenceRow(
                        fieldName,
                        namePath(fieldPath),
                        target,
                        renderContext,
                        configForNested(target),
                        objectPathTitle(target),
                        exp);

        if (!exp) {
            return chip;
        }

        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);

        wrap.add(chip, GridBagUtils.gbc(
                0, 0, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 0)));

        // The chip directly above already shows the target's name, so the
        // expanded body must not repeat it as its own title header.
        JComponent inline = inlineViewable(target, fieldPath, true);

        if (inline != null) {
            wrap.add(inline, GridBagUtils.gbc(
                    0, 1, 1.0, 0.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.HORIZONTAL,
                    new Insets(0, 16, 4, 0)));
        }

        return wrap;
    }

    private TextBlock.Row textBlockRow(
            String fieldName,
            List<String> fieldPath,
            Object value) {

        List<String> lines = new ArrayList<>();

        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    lines.add("• " + item);
                }
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                lines.add(String.valueOf(e.getKey()) + " -> " + String.valueOf(e.getValue()));
            }
        } else {
            lines.add(String.valueOf(value));
        }

        return new TextBlock.Row(
                fieldName,
                new ArrayList<>(fieldPath),
                value,
                lines);
    }

    private boolean isTextBlockCandidate(Field field, Object value) {
        if (value == null || isEmptyCollectionOrMap(value)) {
            return false;
        }

        if (ViewableAdapter.isReference(field)
                || ViewableAdapter.isProvenanceField(field)) {
            return false;
        }

        // @Link string fields render as a dedicated clickable row rather
        // than folding into the (drag-to-select) text block.
        if (ViewableAdapter.isLinkField(field)
                && value instanceof String s
                && !s.isBlank()) {
            return false;
        }

        if (value instanceof Viewable) {
            return false;
        }

        if (value instanceof ImagePane || value instanceof MediaValue) {
            return false;
        }

        // A collection or map renders as its own bordered, collapsible group
        // ("{field} (N)" header) — never folded into the shared, drag-to-select
        // text block. This matches the dynamic-field path (which already treats
        // every collection/map as complex) and keeps a long list (e.g. a log
        // node's `messages`) collapsible instead of an unbounded bullet run.
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return false;
        }

        return true;
    }

    private static List<String> namePath(List<String> base) {
        List<String> out =
                new ArrayList<>(base == null ? List.of() : base);

        if (out.isEmpty()
                || !"name".equals(out.get(out.size() - 1))) {
            out.add("name");
        }

        return out;
    }

    private boolean isEmptyCollectionOrMap(Object value) {
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }

        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }

        return false;
    }

    private ViewConfig defaultConfigForValue(Object value) {
        if (value instanceof Viewable q) {
            return configForNested(q);
        }

        if (value instanceof Collection<?> col) {
            for (Object item : col) {
                if (item instanceof Viewable q) {
                    return configForNested(q);
                }
            }
        }

        if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                if (v instanceof Viewable q) {
                    return configForNested(q);
                }
            }
        }

        return ViewConfig.leaf()
                         .setAddListener(config.isAddListener())
                         .setThumb(config.isThumb());
    }

    private ViewConfig configForNested(Viewable q) {
        ViewConfig fromContext =
                renderContext.configFor(q.getClass());

        if (fromContext != null) {
            return fromContext
                    .setAddListener(config.isAddListener())
                    .setThumb(config.isThumb());
        }

        return ViewConfig.all(q.getClass())
                         .setAddListener(config.isAddListener())
                         .setThumb(config.isThumb());
    }

    private Set<Object> copyVisited() {
        Set<Object> copy = identitySetOf();
        copy.addAll(visited);
        return copy;
    }

    private Set<Object> copyAncestors() {
        Set<Object> copy = identitySetOf();
        copy.addAll(ancestors);
        return copy;
    }

    private void addSingle(Component comp, int row) {
        renderedConfiguredContent = true;

        add(comp, GridBagUtils.gbc(
                0, row,
                1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(2, 2, 2, 2)));
    }

    private void ensureTitleHasRoom() {
        String title = getTitle();

        if (title == null || title.isEmpty()) {
            return;
        }

        if (getComponentCount() == 0) {
            Font font = UIManager.getFont("TitledBorder.font");

            if (font == null) {
                font = getFont();
            }

            FontMetrics fm = getFontMetrics(font);

            Dimension d = new Dimension(
                    Math.max(140, fm.stringWidth(title) + 30),
                    Math.max(40, fm.getHeight() + 18));

            setPreferredSize(d);
            setMinimumSize(d);
        }
    }

    private void addFocusTopLevelListener(Component c, Viewable q) {
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.isConsumed()) {
                    return;
                }

                e.consume();

                if (!renderContext.focusTopLevel(q)) {
                    openInFrame(q);
                }
            }
        });
    }

    private static String shortValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Collection<?> c) {
            return "Collection size=" + c.size();
        }
        if (v instanceof Map<?, ?> m) {
            return "Map size=" + m.size();
        }

        String s = String.valueOf(v);
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }


    private void addOpenListener(Component c, Viewable q) {
        //log.debug("ADD open listener to " + q.getName());
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.isConsumed()) {
                    return;
                }
                if (e.getClickCount() == 2) {
                    e.consume();
                    openInFrame(q);
                }
            }
        });
    }

    private void openInFrame(Viewable q) {
        new CardFrame(q,
                      ViewConfig.allWithMinorFields(q.getClass())
                                    .setAddListener(config.isAddListener())
                                    .setThumb(config.isThumb()));
    }

    private String safeName(Viewable q) {
        String n = q == null ? null : q.getName();
        return n == null ? "" : n;
    }

    // Replace a query image with its answer-hiding version (hand mask, else
    // runtime OCR). Best-effort: returns the original ImagePane on any failure.
    private Object blurForQuiz(ImagePane original) {
        String type = viewable.typeName();
        String name = viewable.getDisplayName();
        try {
            ImageBlurrer blurrer = ImageBlurrer.active();
            if (!blurrer.blurs(type, name)) {
                return original;
            }
            java.awt.image.BufferedImage src =
                    toBufferedImage(original.getCachedImage().getFullImage());
            java.awt.image.BufferedImage blurred =
                    blurrer.blur(type, name, src);
            if (blurred == src) {
                return original;
            }
            return new ImagePane(name, viewable, new objectview.utils.swing.CachedImage(blurred), false, false);
        } catch (Throwable e) {
            return original;
        }
    }

    private static java.awt.image.BufferedImage toBufferedImage(java.awt.Image img) {
        if (img instanceof java.awt.image.BufferedImage b) {
            return b;
        }
        java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(
                Math.max(1, img.getWidth(null)), Math.max(1, img.getHeight(null)),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return b;
    }

    // A thin wrapper whose own name IS a single child's name (President ->
    // Person, both "George Washington"; the name was historically the shared
    // identifier). Drop this card's bold title so the name shows once -- on the
    // child's chip, which keeps its Open-in-window / expand behaviour.
    private boolean wrapsSameNameChild() {
        if (viewable == null) {
            return false;
        }
        String owner = safeName(viewable);
        if (owner.isEmpty()) {
            return false;
        }
        boolean sameNamedChild = false;
        int otherValuedFields = 0;
        for (Field field : config.visibleFieldsFor(viewable.getClass())) {
            if ("name".equals(field.getName())) {
                continue;
            }
            Object value;
            try {
                value = field.get(viewable);
            } catch (Exception e) {
                continue;
            }
            if (value == null) {
                continue;
            }
            if (value instanceof Viewable child && owner.equals(safeName(child))) {
                sameNamedChild = true;
            } else {
                otherValuedFields++;
            }
        }
        // Only a thin wrapper whose *sole* content is the same-named child
        // suppresses its own title (to avoid echoing the name). A full card
        // that merely has a coincidentally same-named reference field — e.g.
        // the constellation Andromeda whose "named after" is the figure
        // Andromeda — must still show its title.
        return sameNamedChild && otherValuedFields == 0;
    }

    public Viewable getViewable() {
        return viewable;
    }

    /**
     * Expands any collapsed collection/map lying on {@code path} (relative to
     * this card's viewable), so a search match hidden inside a collapsed list
     * becomes rendered (and thus highlightable / scrollable). Only flips
     * currently-collapsed collections; returns true if anything changed, so the
     * caller can {@link #refresh()} once. Does not itself refresh.
     */
    public boolean expandCollectionsOnPath(List<String> searchPath) {
        if (searchPath == null || searchPath.isEmpty() || viewable == null) {
            return false;
        }
        return expandCollectionsAlong(viewable, searchPath, 0);
    }

    private boolean expandCollectionsAlong(Object obj, List<String> path, int idx) {
        if (obj == null || idx > path.size()) {
            return false;
        }

        // Force-expand a nested Viewable rendered as a reference CHIP (e.g. a query
        // log's `steps` item) so a match deeper inside it renders + highlights.
        // idx>0 skips the root card itself; idx<size means the hit is INSIDE it.
        boolean changed = false;
        if (idx > 0 && idx < path.size() && obj instanceof Viewable q
                && renderContext != null && !renderContext.isTopLevel(q)
                && renderContext.setExpanded(q, true)) {
            changed = true;
        }

        if (obj instanceof Collection<?> c) {
            changed |= expandIfCollapsed(obj, c.size());
            for (Object item : c) {
                changed |= expandCollectionsAlong(item, path, idx);
            }
            return changed;
        }

        if (obj instanceof Map<?, ?> m) {
            changed |= expandIfCollapsed(obj, m.size());
            for (Object v : m.values()) {
                changed |= expandCollectionsAlong(v, path, idx);
            }
            return changed;
        }

        if (idx >= path.size()) {
            return changed;
        }

        String part = path.get(idx);
        // "name" is a synthetic leaf (Viewable.getName()), not a real field.
        if ("name".equals(part)) {
            return changed;
        }

        Field f = ViewableAdapter.getField(obj.getClass(), part);
        if (f == null) {
            return changed;
        }
        try {
            f.setAccessible(true);
            return changed | expandCollectionsAlong(f.get(obj), path, idx + 1);
        } catch (Exception e) {
            return changed;
        }
    }

    private boolean expandIfCollapsed(Object collectionKey, int count) {
        boolean defaultExpanded = count <= COLLECTION_COLLAPSE_THRESHOLD;
        if (!renderContext.isCollectionExpanded(collectionKey, defaultExpanded)) {
            renderContext.setCollectionExpanded(collectionKey, true);
            return true;
        }
        return false;
    }

    public String getTitle() {
        return (config.isAllFields() || config.getFields().containsKey("name"))
                ? safeName(viewable)
                : "";
    }
}