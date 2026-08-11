package objectview.render;

import objectview.*;
import objectview.annotations.Inline;
import objectview.annotations.Reference;
import objectview.demo.CardFrame;
import objectview.media.ImageBlurrer;
import objectview.media.ImagePane;
import objectview.media.MediaValue;
import objectview.field.FieldKind;
import objectview.field.FieldPath;
import objectview.field.FieldRef;
import objectview.field.FieldSet;
import objectview.field.FieldProperties;
import objectview.viewconfig.ViewConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import objectview.utils.swing.GridBagUtils;
import objectview.annotations.Link;

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
public class Card extends JPanel implements RenderedInstanceHost {

    private static final Logger log = LoggerFactory.getLogger(Card.class);

    // A complex collection/map field renders under a collapsible header,
    // collapsed by default (threshold 0 => no list auto-expands); click the
    // header to expand. Toggleable per collection.
    private final Viewable viewable;
    private final ViewConfig config;
    private final boolean fill;
    // True only for a top-level instance card (not a nested reference/value sub-card), so a
    // header decoration (e.g. an identity chip) attaches to instances, never to nested cards.
    private final boolean rootRender;

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

    @Override
    public void setHighlightColor(Color c) {
        this.highlightColor = c;
        repaint();
    }

    /** Whether this card currently carries a search-hit highlight. */
    @Override
    public boolean isHighlighted() {
        return highlightColor != null;
    }

    /** A card reveals by expanding the collections on the path, in place, after it
     *  has been materialized. */
    @Override
    public boolean revealPath(FieldPath path) {
        return expandCollectionsOnPath(path);
    }

    @Override
    public Viewable renderedInstance() {
        return viewable;
    }

    @Override
    protected void paintComponent(Graphics g) {
        InstancePaint.fillHighlight(
                g, highlightColor, getWidth(), getHeight());
        if (gutter != null) {
            gutter.paint(g, this, getHeight());
        }
        super.paintComponent(g);
    }

    /** Flips this card between collapsed and expanded, exactly as its header triangle
     *  does — the gutter is a second place to reach the same action, not a second
     *  implementation of it. */
    void toggleCollapsed() {
        renderContext.toggleCardExpanded(viewable, false);
        renderContext.notifyCardToggled(viewable);
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (renderContext != null && renderContext.isSelected(viewable)) {
            InstancePaint.paintSelection(g, getWidth(), getHeight(), true);
        }
    }

    private final Set<Object> visited;
    private final Set<Object> ancestors;
    // The traversal context with which this exact card was constructed. A nested
    // card refresh must restart from these seeds, not pretend it is a new root;
    // otherwise cycle suppression and shape differ before/after a chip click.
    private final Set<Object> refreshVisitedSeed;
    private final Set<Object> refreshAncestorsSeed;
    private final RenderContext renderContext;
    private boolean renderedConfiguredContent = false;
    // Non-null only while this card is an EXPANDED collapsible root: the strip down
    // its left edge that collapses it. A collapsed card has none.
    private CollapseGutter gutter;

    private final FieldPath path;
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

    /**
     * Renders one already-resolved field through the exact semantic path used
     * inside a card. The caller owns layout; {@code showFieldName=false} is used
     * by a columns view whose header already carries the field label.
     */
    public static JComponent renderFieldComponent(
            Viewable owner,
            FieldRef field,
            FieldPath fieldPath,
            Object value,
            ViewConfig ownerConfig,
            ViewConfig fieldConfig,
            RenderContext renderContext,
            boolean fill,
            boolean showFieldName) {
        if (owner == null || field == null || value == null) return null;
        Card renderer = new Card(
                owner, ownerConfig, renderContext, fill,
                fieldPath == null ? FieldPath.ROOT : fieldPath.parent());
        ViewConfig child = fieldConfig == null
                ? renderer.defaultConfigForValue(value) : fieldConfig;
        return renderer.renderFieldComponent(
                field, value, showFieldName ? field.name() : "",
                fieldPath == null ? FieldPath.ROOT : fieldPath, child);
    }

    /** Lightweight host for the shared field renderer; it builds no card UI. */
    private Card(Viewable owner,
                 ViewConfig config,
                 RenderContext context,
                 boolean fill,
                 FieldPath path) {
        this.viewable = owner;
        this.config = config == null ? ViewConfig.of(owner.getClass()) : config;
        this.renderContext = context == null ? new RenderContext() : context;
        this.fill = fill;
        this.path = path == null ? FieldPath.ROOT : path;
        this.rootRender = false;
        this.visited = identitySetOf();
        this.ancestors = identitySetOf();
        this.refreshVisitedSeed = identitySetOf();
        this.refreshAncestorsSeed = identitySetOf();
        this.visited.add(owner);
        this.ancestors.add(owner);
        setLayout(new GridBagLayout());
        setOpaque(false);
    }

    public Card(Viewable viewable,
                ViewConfig config,
                boolean fill) {
        this(identitySetOf(), identitySetOf(), new RenderContext(),
                true, viewable, config, fill, FieldPath.ROOT, null, null);
    }

    // Root render whose own title is suppressed -- e.g. an "Open in window"
    // frame already shows the name in its title bar.
    public Card(Viewable viewable,
                ViewConfig config,
                boolean fill,
                boolean suppressTitle) {
        this(identitySetOf(), identitySetOf(), new RenderContext(),
                true, viewable, config, fill, FieldPath.ROOT, null, null, suppressTitle);
    }

    public Card(Viewable viewable,
                ViewConfig config,
                Collection<? extends Viewable> topLevel,
                boolean fill) {
        this(identitySetOf(), identitySetOf(), new RenderContext(topLevel),
                true, viewable, config, fill, FieldPath.ROOT, null, null);
    }

    public Card(Viewable viewable,
                ViewConfig config,
                RenderContext renderContext,
                boolean fill) {
        this(identitySetOf(), identitySetOf(), renderContext,
                true, viewable, config, fill, FieldPath.ROOT,
             null, null);
    }

    public Card(Set<Object> visited,
                Set<Object> ancestors,
                RenderContext renderContext,
                boolean rootRender,
                Viewable viewable,
                ViewConfig config,
                boolean fill,
                FieldPath path) {
        this(visited, ancestors, renderContext, rootRender,
                viewable, config, fill, path, null, null);
    }

    public Card(Viewable viewable,
                ViewConfig config,
                boolean fill,
                JComponent compiledView) {
        this(identitySetOf(), identitySetOf(), new RenderContext(),
                true, viewable, config, fill, FieldPath.ROOT,
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
                FieldPath path,
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
                FieldPath path,
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
        this.rootRender = rootRender;
        this.visited = visited == null ? identitySetOf() : visited;
        this.ancestors = ancestors == null ? identitySetOf() : ancestors;
        this.refreshVisitedSeed = identityCopy(this.visited);
        this.refreshAncestorsSeed = identityCopy(this.ancestors);
        this.renderContext = renderContext == null
                ? new RenderContext()
                : renderContext;
        this.fill = fill;
        this.path = path == null ? FieldPath.ROOT : path;

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

        buildConfiguredContent();
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

        restoreTraversalContext();
        buildConfiguredContent();

        revalidate();
        repaint();
    }

    /** The one construction/refresh path for reflected card content. */
    private void buildConfiguredContent() {
        if (!rootRender && ancestors.contains(viewable)) {
            addCompactReference(viewable, false);
            return;
        }
        if (!rootRender && visited.contains(viewable)) {
            addCompactReference(viewable, false);
            return;
        }
        if (!rootRender && renderContext.isTopLevel(viewable)) {
            addCompactReference(viewable, true);
            return;
        }

        visited.add(viewable);
        ancestors.add(viewable);
        if (rootRender && renderContext.collapsibleCards()) {
            buildCollapsibleRoot();
        } else {
            addTitleHeaderIfNeeded();
            buildFields();
            ensureTitleHasRoom();
        }
        ancestors.remove(viewable);
    }

    private void restoreTraversalContext() {
        visited.clear();
        visited.addAll(refreshVisitedSeed);
        ancestors.clear();
        ancestors.addAll(refreshAncestorsSeed);
    }

    private static Set<Object> identityCopy(Set<Object> source) {
        Set<Object> copy = identitySetOf();
        if (source != null) copy.addAll(source);
        return copy;
    }

    @Override
    public void refreshRenderedContent() {
        refresh();
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

        // An expanded card gets a clickable left edge, so it can be collapsed from
        // wherever the reader has scrolled to instead of only from its header.
        if (expanded) {
            gutter = CollapseGutter.INSTANCE;
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true),
                    BorderFactory.createEmptyBorder(4, CollapseGutter.WIDTH, 4, 4)));
            CollapseGutter.install(this);
        }

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
                toggleCollapsed();
            }
        });

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0, 80, 180));
        titleLabel.putClientProperty(FieldProperties.FIELD_NAME_PROPERTY,
                displayFieldKey(viewable));
        titleLabel.putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, title);
        titleLabel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY,
                path.append(displayFieldKey(viewable)));

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
        attachDecoration(header);
        return header;
    }

    /** Place the caller-supplied header decoration (e.g. an identity chip) in the header's
     *  trailing slot, when the render context provides one for this card's viewable. The card
     *  never interprets the component — identity/provenance presentation stays outside it. */
    private void attachDecoration(JPanel header) {
        if (!rootRender || renderContext == null) {
            return;
        }
        JComponent decoration = renderContext.cardDecoration(viewable);
        if (decoration != null) {
            header.add(decoration, BorderLayout.EAST);
        }
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

        titleLabel.putClientProperty(FieldProperties.FIELD_NAME_PROPERTY,
                displayFieldKey(q));
        titleLabel.putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, title);
        titleLabel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY,
                path.append(displayFieldKey(q)));

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
        attachDecoration(header);

        return header;
    }

    private void addCompactReference(Viewable q, boolean focusTopLevel) {
        ViewConfig openCfg = configForNested(q);

        addSingle(
                new ReferenceRow(
                        "",
                        path,
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

    // Renders scalar MEDIA field(s) at the top of the card, regardless of whether
    // they are declared Java fields or dynamic map entries.
    private int appendHoistedMedia(
            FieldSet fields, int row, java.util.Set<String> hoisted) {
        for (FieldRef field : fields.fields()) {
            String name = field.name();
            if (field.role() != objectview.field.FieldRole.NONE) {
                continue;
            }
            if (!shows(field)) {
                continue;
            }
            Object value = fields.read(name);
            if (value == null || field.collection()
                    || field.valueKind() != FieldKind.MEDIA) {
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
        FieldSet fields = FieldSet.of(
                viewable, renderContext.fieldSchema(viewable));

        // Hoist a MEDIA field (a portrait / flag — ImagePane or MediaValue) to the TOP
        // of the card so the entity's image reads as its avatar instead of sitting
        // buried among the text fields; the hoisted field is skipped in the pass below.
        java.util.Set<String> hoistedMedia = new java.util.HashSet<>();
        row = appendHoistedMedia(fields, row, hoistedMedia);

        for (FieldRef field : inConfigOrder(fields.fields())) {
            String name = field.name();

            if (field.role() != objectview.field.FieldRole.NONE || hoistedMedia.contains(name)
                    || !shows(field)) {
                continue;
            }

            Object value = fields.read(name);

            // A field that renders nothing must not break the text-block
            // batch: a null/empty leaf (e.g. a blank error) sitting between
            // two value leaves would otherwise split them into separate
            // blocks and open a stray, variable vertical gap.
            if (value == null || isEmptyCollectionOrMap(value)) {
                continue;
            }

            FieldPath fieldPath = path.append(name);

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
        if (path.isRoot()) {
            add(Box.createGlue(), GridBagUtils.gbc(
                    0, row + 1, 1.0, 1.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 0)));
        }
    }

    private List<FieldRef> inConfigOrder(List<FieldRef> fields) {
        return orderFieldsByConfig(fields,
                config == null ? java.util.Set.of() : config.getFields().keySet());
    }

    /**
     * Render explicitly-configured fields in the CONFIG's order (the same ordered
     * {@code ViewConfig.getFields()} that sort/search consume), so the view config's move
     * before/after actually reorders the card. Fields named in {@code configuredOrder}
     * come first, in that order; implied fields (all-fields / all-minor, never named)
     * keep their schema order, appended after. An empty order (an all-fields config names
     * nothing) leaves the cards unchanged.
     */
    static List<FieldRef> orderFieldsByConfig(
            List<FieldRef> fields, java.util.Set<String> configuredOrder) {
        if (configuredOrder == null || configuredOrder.isEmpty()) {
            return fields;
        }
        java.util.LinkedHashMap<String, FieldRef> remaining =
                new java.util.LinkedHashMap<>();
        for (FieldRef field : fields) {
            remaining.put(field.name(), field);
        }
        List<FieldRef> ordered = new ArrayList<>();
        for (String name : configuredOrder) {
            FieldRef field = remaining.remove(name);
            if (field != null) {
                ordered.add(field);
            }
        }
        ordered.addAll(remaining.values());
        return ordered;
    }

    private boolean shows(FieldRef field) {
        if (field == null) {
            return false;
        }
        if (config.hasField(field.name())) {
            return true;
        }
        return field.minor()
                ? config.isAllMinorFields() : config.isAllFields();
    }

    private int addTextBlock(List<TextBlock.Row> rows, int row) {
        TextBlock block = new TextBlock(rows);

        if (!block.isEmpty()) {
            addSingle(block, row++);
        }

        return row;
    }

    private int addRenderedField(FieldRef field, Object value, int row) {
        if (value == null || isEmptyCollectionOrMap(value)) {
            return row;
        }

        String fieldName = field.name();
        FieldPath fieldPath = path.append(fieldName);
        ViewConfig fieldCfg = config.getFieldConfig(fieldName);
        if (fieldCfg == null) {
            fieldCfg = defaultConfigForValue(value);
        }

        JComponent component = renderFieldComponent(
                field, value, fieldName, fieldPath, fieldCfg);
        if (component != null) {
            addSingle(component, row++);
        }
        return row;
    }

    /**
     * The shared semantic field-rendering stage. Card and table layouts call
     * this before the terminal {@link ValueRenderer}, so annotations, reference
     * behaviour, child config and collection policy cannot diverge by layout.
     */
    private JComponent renderFieldComponent(
            FieldRef field,
            Object value,
            String fieldName,
            FieldPath fieldPath,
            ViewConfig fieldCfg) {
        if (value == null || isEmptyCollectionOrMap(value)) return null;

        boolean isCollectionOrMap =
                value instanceof Collection<?> || value instanceof Map<?, ?>;

        if (field.annotatedReference()) {
            if (isCollectionOrMap) {
                Object v = value;
                ViewConfig cfg = fieldCfg;
                return collapsibleCollectionComponent(fieldName, fieldPath, value,
                        () -> createReferenceFieldComponent(
                                "", fieldPath, v, cfg));
            }
            return createReferenceFieldComponent(
                    fieldName, fieldPath, value, fieldCfg);
        }

        if (field.inline()) {
            return createInlineFieldComponent(
                    fieldName, fieldPath, value, fieldCfg);
        }

        if (field.link()
                && value instanceof String url
                && !url.isBlank()) {
            return new LinkRow(fieldName, fieldPath, url, field.linkText());
        }

        if (value instanceof Viewable q) {
            return collapsibleReference(
                    fieldName, fieldPath, q, false, fieldCfg);
        }

        if (value instanceof ImagePane ip && config.isBlurImages() && viewable != null) {
            value = blurForQuiz(ip);
        }

        if (isCollectionOrMap) {
            ViewConfig cfg = fieldCfg;
            Object collValue = value;
            return collapsibleCollectionComponent(fieldName, fieldPath, value,
                    () -> ValueRenderer.createFieldComponent(
                            copyVisited(), copyAncestors(), renderContext,
                            "", fieldPath, collValue, cfg, fill));
        }

        return ValueRenderer.createFieldComponent(
                copyVisited(),
                copyAncestors(),
                renderContext,
                fieldName,
                fieldPath,
                value,
                fieldCfg,
                fill);
    }

    // Renders a complex collection/map field as a collapsible group: a clickable
    // "{field} (N)" header plus, when expanded, the items built by {@code body}.
    // Lists over COLLECTION_COLLAPSE_THRESHOLD start collapsed; the per-collection
    // toggle is remembered in the render context (keyed by the collection's
    // identity), and the body is built only when expanded so a collapsed long
    // list stays cheap.
    private JComponent collapsibleCollectionComponent(
            String fieldName,
            FieldPath fieldPath,
            Object value,
            java.util.function.Supplier<JComponent> body) {

        return CollapsibleFieldRenderer.create(
                fieldName, fieldPath, value, value, renderContext, body);
    }

    private JComponent createReferenceFieldComponent(
            String fieldName,
            FieldPath fieldPath,
            Object value,
            ViewConfig nestedConfig
                                                    ) {
        if (value instanceof Viewable q) {
            return collapsibleReference(
                    fieldName, fieldPath, q, false, nestedConfig);
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
                    addReferenceToPanel(
                            panel, "", q, fieldPath, nestedConfig, row++);
                }
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (item instanceof Viewable q) {
                    addReferenceToPanel(
                            panel, "", q, fieldPath, nestedConfig, row++);
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
            FieldPath fieldPath,
            Object value,
            ViewConfig nestedConfig) {

        if (value instanceof Viewable q) {
            return inlineViewable(q, fieldPath, nestedConfig, false);
        }

        Collection<?> items =
                value instanceof Collection<?> c ? c
                        : value instanceof Map<?, ?> m ? m.values()
                        : List.of();

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        if (fieldName != null && !fieldName.isBlank()) {
            // Show the size in the title, as the collapsible CollectionHeader does for
            // groups/languages — an @Inline collection (e.g. a query log's `steps`)
            // otherwise showed just "steps" with no count. Re-rendered on refresh, so the
            // number tracks the collection as it changes.
            long count = items.stream().filter(Viewable.class::isInstance).count();
            panel.setBorder(BorderFactory.createTitledBorder(fieldName + " (" + count + ")"));
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
            JComponent nested = collapsibleReference(
                    "", fieldPath, q, false, nestedConfig);

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

    private JComponent inlineViewable(Viewable q, FieldPath fieldPath) {
        return inlineViewable(q, fieldPath, configForNested(q), false);
    }

    // suppressTitle: the name is already shown above (the chip that expanded
    // into this body, or a same-named wrapper), so don't repeat it as a title.
    private JComponent inlineViewable(
            Viewable q,
            FieldPath fieldPath,
            ViewConfig nestedConfig,
            boolean suppressTitle) {
        Card nested =
                new Card(
                        copyVisited(),
                        copyAncestors(),
                        renderContext,
                        false,
                        q,
                        nestedConfig,
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
            FieldPath fieldPath,
            ViewConfig nestedConfig,
            int row
    ) {
        panel.add(collapsibleReference(
                        fieldName, fieldPath, q, false, nestedConfig),
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
            FieldPath fieldPath,
            Viewable target) {
        return collapsibleReference(
                fieldName, fieldPath, target, false, configForNested(target));
    }

    // As above, but {@code defaultExpanded} seeds the initial state when the user
    // hasn't toggled this reference yet — true for a reference that used to render
    // always-inline (a dynamic map field), so it looks the same but is now a
    // collapsible chip rather than a fixed inline panel.
    private JComponent collapsibleReference(
            String fieldName,
            FieldPath fieldPath,
            Viewable target,
            boolean defaultExpanded) {
        return collapsibleReference(fieldName, fieldPath, target,
                defaultExpanded, configForNested(target));
    }

    private JComponent collapsibleReference(
            String fieldName,
            FieldPath fieldPath,
            Viewable target,
            boolean defaultExpanded,
            ViewConfig nestedConfig) {

        ViewConfig targetConfig = nestedConfig == null
                ? configForNested(target) : nestedConfig;

        // A reference to something that is itself a top-level card in this view
        // is a navigation link (jump to that card) rather than an expand-in-place
        // chip — so the same object never has two competing expand toggles.
        if (renderContext != null && renderContext.isTopLevel(target)) {
            return decoratedReference(new ReferenceRow(
                    fieldName,
                    fieldPath,
                    target,
                    renderContext,
                    targetConfig,
                    objectPathTitle(target),
                    false,
                    true), target);
        }

        // A reference with nothing behind it is a value, not a door. Its target's only
        // field is its display name — which the row already shows — so the triangle
        // opens an empty box. Rendered as a plain row it keeps selection, search
        // highlight and copy, and stops promising content it does not have.
        if (!targetHasContent(target)) {
            return decoratedReference(
                    new TextRow(fieldName, fieldPath, ReferenceRow.referenceLabel(target)),
                    target);
        }

        boolean exp = renderContext != null
                && renderContext.isExpanded(target, defaultExpanded);

        ReferenceRow chip =
                new ReferenceRow(
                        fieldName,
                        fieldPath,
                        target,
                        renderContext,
                        targetConfig,
                        objectPathTitle(target),
                        exp);

        if (!exp) {
            return decoratedReference(chip, target);
        }

        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);

        wrap.add(decoratedReference(chip, target), GridBagUtils.gbc(
                0, 0, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 0)));

        // The chip directly above already shows the target's name, so the
        // expanded body must not repeat it as its own title header.
        JComponent inline = inlineViewable(
                target, fieldPath, targetConfig, true);

        if (inline != null) {
            wrap.add(inline, GridBagUtils.gbc(
                    0, 1, 1.0, 0.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.HORIZONTAL,
                    new Insets(0, 16, 4, 0)));
        }

        return wrap;
    }

    // Reuse the card-header identity decorator (e.g. TransformApp's clickable QID chip) on a
    // reference chip too, so a referenced entity surfaces its identity the SAME way a card
    // does — no bespoke identity rendering. Scoped to a non-null decoration, so a plain
    // reference (identity not actionable) stays a plain chip.
    /**
     * Whether {@code target} holds anything beyond its own identity.
     *
     * <p>A property of the OBJECT, deliberately not of the view config. A config that
     * renders a reference as a leaf ({@link ViewConfig#leaf()}, how a table cell shows
     * one) hides the target's fields on purpose, and that is an authoring choice the
     * chip should still honour — the target can be opened in its own window. Having no
     * fields at all is not a choice, it is a fact about the data, and it is the only
     * case where an expander can promise nothing.
     *
     * <p>Identity and display fields do not count: the reference row already shows them.
     * Neither does a null, blank or empty value — those render nothing.
     */
    private boolean targetHasContent(Viewable target) {
        if (target == null || renderContext == null) {
            return true;   // unknown: keep the expander rather than hide content
        }
        FieldSet fields = FieldSet.of(target, renderContext.fieldSchema(target));
        for (FieldRef field : fields.fields()) {
            if (field.role() == objectview.field.FieldRole.NONE
                    && hasContent(fields.read(field.name()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasContent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        if (value instanceof java.util.Collection<?> items) {
            return !items.isEmpty();
        }
        if (value instanceof java.util.Map<?, ?> entries) {
            return !entries.isEmpty();
        }
        return true;
    }

    private JComponent decoratedReference(JComponent chip, Viewable target) {
        return decorateReference(renderContext, chip, target);
    }

    /** Shared reference decoration for Card fields and ValueRenderer collection items. */
    static JComponent decorateReference(
            RenderContext context, JComponent chip, Viewable target) {
        JComponent decoration = context == null
                ? null : context.cardDecoration(target);
        if (decoration == null) {
            return chip;
        }
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(chip, BorderLayout.CENTER);
        row.add(decoration, BorderLayout.EAST);
        return row;
    }

    private TextBlock.Row textBlockRow(
            String fieldName,
            FieldPath fieldPath,
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
                fieldPath,
                value,
                lines);
    }

    private boolean isTextBlockCandidate(FieldRef field, Object value) {
        if (value == null || isEmptyCollectionOrMap(value)) {
            return false;
        }

        if (field.annotatedReference()) {
            return false;
        }

        // @Link string fields render as a dedicated clickable row rather
        // than folding into the (drag-to-select) text block.
        if (field.link()
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
    public boolean expandCollectionsOnPath(FieldPath searchPath) {
        return renderContext != null
                && renderContext.revealPath(viewable, searchPath);
    }

    public String getTitle() {
        String displayKey = displayFieldKey(viewable);
        return (config.isAllFields()
                || config.getFields().containsKey(displayKey)
                || config.getFields().containsKey(
                        objectview.field.ViewableContractFieldSet.DISPLAY_KEY))
                ? safeName(viewable)
                : "";
    }

    private String displayFieldKey(Viewable value) {
        if (value == null) {
            return objectview.field.ViewableContractFieldSet.DISPLAY_KEY;
        }
        return objectview.field.ViewableContractFieldSet.displayKey(
                FieldSet.of(value, renderContext == null
                        ? null : renderContext.fieldSchema(value)));
    }
}
