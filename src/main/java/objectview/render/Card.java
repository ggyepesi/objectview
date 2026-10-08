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
import objectview.plan.Disclosure;
import objectview.plan.DecisionRenderer;
import objectview.plan.ObjectPlan;
import objectview.plan.PlanResolver;
import objectview.plan.RenderExecutor;
import objectview.plan.RenderSink;
import objectview.plan.Representation;
import objectview.plan.TypeShape;
import objectview.plan.ViewConfigDesugar;
import objectview.plan.ViewDefaults;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.VirtualizedCardList;
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
 *   <li>{@link Inline @Inline} — render owned nested Viewable(s) inline. Collection
 *       members remain individually collapsible and large collections are
 *       virtualized; never use it to imply ownership on broad/cyclic graphs.</li>
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
 * {@link RenderContext} and refreshes the nearest nested render boundary via
 * {@link #refresh()}, then remeasures the retained top-level card. The
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
    private static final String INLINE_TITLE = "objectview.inlineTitle";
    private static final String INLINE_ITEMS = "objectview.inlineItems";
    private static final String INLINE_RENDERED = "objectview.inlineRendered";
    private static final String INLINE_FIELD_PATH = "objectview.inlineFieldPath";
    private static final String INLINE_FIELD_PLAN = "objectview.inlineFieldPlan";
    private static final String INLINE_OCCURRENCE = "objectview.inlineOccurrence";
    private static final String INLINE_ANCESTORS = "objectview.inlineAncestors";
    private static final String INLINE_ITEM_COUNT = "objectview.inlineItemCount";
    private static final String INLINE_VIRTUAL_LIST = "objectview.inlineVirtualList";
    /** Large inline collections get their own viewport instead of one Swing component per item. */
    private static final int INLINE_VIRTUALIZATION_THRESHOLD = 200;
    private static final int INLINE_VIRTUAL_HEIGHT = 520;

    // A complex collection/map field renders under one collapsible header.
    // Ordinary collections start folded; singleton media and explicitly inline
    // content supply their established visible-by-default presentation policy.
    private final Viewable viewable;
    private final ViewConfig config;
    private final boolean fill;
    // True only for a top-level instance card (not a nested reference/value sub-card), so a
    // header decoration (e.g. an identity chip) attaches to instances, never to nested cards.
    private final boolean rootRender;
    // Structural inline values may also be listed as top-level objects elsewhere in
    // a MultiView. Unlike a reference, that must not suppress their embedded body.
    private final boolean embedTopLevel;

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

    /** Scrolls the exact collection members retained by field-path resolution into
     * view, after {@link #revealPath} has opened the collections holding them. */
    @Override
    public Component revealPathMember(
            FieldPath path, List<Viewable> collectionMembers) {
        return revealPathMemberIn(this, path, collectionMembers);
    }

    /** Shared member-routing implementation for every host built from Card's field
     * renderer, including table rows. The collection metadata lives on rendered
     * field panels, not on the outer layout. */
    public static Component revealPathMemberIn(
            Container root, FieldPath path, List<Viewable> collectionMembers) {
        if (collectionMembers == null || collectionMembers.isEmpty()) return null;
        // A table cell may flatten a nested route straight to its leaf row. Prefer
        // that exact retained identity when present; cards that render intermediate
        // member containers fall through to the stepwise route below.
        Component direct = revealMemberIn(root, path,
                collectionMembers.get(collectionMembers.size() - 1));
        if (direct != null) return direct;
        Container scope = root;
        Component deepest = null;
        for (Viewable member : collectionMembers) {
            Component rendered = revealMemberIn(scope, path, member);
            if (rendered == null) return deepest;
            deepest = rendered;
            if (rendered instanceof Container container) scope = container;
        }
        return deepest;
    }

    /** Records a rendered leaf against the member identity that produced it. Table
     * cells flatten paths while cards nest them, but both publish the same metadata
     * and are discovered by {@link #revealPathMemberIn}. */
    public static void registerRenderedMember(
            JComponent container, FieldPath path,
            Viewable member, JComponent rendered) {
        if (container == null || path == null || member == null || rendered == null) {
            return;
        }
        container.putClientProperty(INLINE_FIELD_PATH, path);
        Object stored = container.getClientProperty(INLINE_RENDERED);
        @SuppressWarnings("unchecked")
        Map<Viewable, JComponent> members = stored instanceof Map<?, ?> map
                ? (Map<Viewable, JComponent>) map : new IdentityHashMap<>();
        members.put(member, rendered);
        container.putClientProperty(INLINE_RENDERED, members);
    }

    private static Component revealMemberIn(
            Container parent, FieldPath path, Viewable member) {
        if (parent instanceof JComponent panel
                && holdsPath(panel, path)
                && panel.getClientProperty(INLINE_VIRTUAL_LIST)
                        instanceof VirtualizedCardList virtual) {
            JComponent rendered = virtual.ensureVisible(member);
            if (rendered != null) return rendered;
        }
        if (parent instanceof JComponent panel
                && holdsPath(panel, path)
                && panel.getClientProperty(INLINE_RENDERED)
                        instanceof Map<?, ?> rendered) {
            Object exact = rendered.get(member);
            if (exact instanceof Component found) return found;
        }
        for (Component component : parent.getComponents()) {
            if (component instanceof Container nested) {
                Component found = revealMemberIn(nested, path, member);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean holdsPath(JComponent panel, FieldPath path) {
        return panel.getClientProperty(INLINE_FIELD_PATH) instanceof FieldPath held
                && path != null && !held.isRoot()
                && path.size() >= held.size()
                && path.segments().subList(0, held.size()).equals(held.segments());
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

    /** Gives this card a collapse strip down its left edge, running {@code onCollapse}.
     *  The action differs by host: a root card collapses itself, an expanded reference
     *  body collapses the chip that opened it. */
    void armCollapseGutter(Runnable onCollapse) {
        gutter = CollapseGutter.INSTANCE;
        CollapseGutter.install(this, onCollapse);
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
        FieldPath at = fieldPath == null ? FieldPath.ROOT : fieldPath;
        Card renderer = new Card(owner, ownerConfig, renderContext, fill, at.parent());
        // The column's child config, rewritten against the field's own shape when it
        // still carries shorthand; an absent one ticks nothing under the field (rule 9).
        ViewConfig child = fieldConfig == null ? ViewConfig.leaf()
                : ViewConfigDesugar.isLiteral(fieldConfig) ? fieldConfig
                : ViewConfigDesugar.literal(fieldConfig,
                        renderer.renderContext.shape(owner).nested(field));
        ObjectPlan.FieldPlan plan = new ObjectPlan.FieldPlan(
                field, PlanResolver.representation(field), child);
        Set<Object> ancestors = identitySetOf();
        ancestors.add(owner);
        RenderExecutor.Decision decision = renderer.renderContext.executor().field(
                plan, value, new RenderSink.Occurrence(at, field.label(), at.dotted()),
                ancestors);
        return renderer.paint(decision, showFieldName ? field.name() : "", ancestors);
    }

    /** Lightweight host for the shared field renderer; it builds no card UI. */
    private Card(Viewable owner,
                 ViewConfig config,
                 RenderContext context,
                 boolean fill,
                 FieldPath path) {
        this.viewable = owner;
        this.renderContext = context == null ? new RenderContext() : context;
        this.config = literal(owner, config, this.renderContext);
        this.fill = fill;
        this.path = path == null ? FieldPath.ROOT : path;
        this.rootRender = false;
        this.embedTopLevel = false;
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

    /** Root render in a detached frame whose title already names the object. */
    public Card(Viewable viewable,
                ViewConfig config,
                RenderContext renderContext,
                boolean fill,
                boolean suppressTitle) {
        this(identitySetOf(), identitySetOf(), renderContext,
                true, viewable, config, fill, FieldPath.ROOT,
                null, null, suppressTitle);
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
        this(visited, ancestors, renderContext, rootRender, viewable, config, fill,
                path, objectPath, compiledView, suppressTitle, false);
    }

    private Card(Set<Object> visited,
                Set<Object> ancestors,
                RenderContext renderContext,
                boolean rootRender,
                Viewable viewable,
                ViewConfig config,
                boolean fill,
                FieldPath path,
                List<Viewable> objectPath,
                JComponent compiledView,
                boolean suppressTitle,
                boolean embedTopLevel) {
        this.suppressTitle = suppressTitle;
        this.embedTopLevel = embedTopLevel;
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

        this.config = literal(viewable, config, this.renderContext);

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
        installCardSelectionListeners(this);
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
        installCardSelectionListeners(this);

        revalidate();
        repaint();
    }

    /**
     * Directive 24 at the card boundary, until the editor emits literal configs (plan
     * phase 6): shorthand is rewritten here once against this object's shape, and every
     * nested card receives an already literal child config. A missing config is the one
     * default.
     */
    private static ViewConfig literal(Viewable viewable, ViewConfig config,
                                      RenderContext context) {
        TypeShape shape = viewable == null ? null
                : context == null ? TypeShape.of(viewable, null, null)
                : context.shape(viewable);
        return config == null
                ? ViewDefaults.newView(shape) : ViewConfigDesugar.preparedView(config, shape);
    }

    // This occurrence's decisions come from the render context's one executor.
    private RenderExecutor.Level level;

    private RenderExecutor.Level level() {
        if (level == null) level = renderContext.executor().level(viewable, config);
        return level;
    }

    /** The one construction/refresh path for reflected card content. Which fields show,
     * how, and whether an object is a link, a back-reference or open is decided by the
     * executor; this card only lays the decisions out. */
    private void buildConfiguredContent() {
        level = renderContext.executor().level(viewable, config);
        visited.add(viewable);
        ancestors.add(viewable);
        // A collapsed card needs a caption; with DISPLAY unticked there is none, so the
        // selected fields show directly instead of an invented header.
        if (rootRender && renderContext.collapsibleCards() && level.caption() != null) {
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

    /** The component tree changed in place; keep it and update only its virtual
     * owner's cached measurement. */
    void notifyOwnerLayoutChanged() {
        if (path.isRoot()) renderContext.notifyCardResized(viewable);
    }

    private void addTitleHeaderIfNeeded() {
        String title = getTitle();

        if (title == null || title.isEmpty() || suppressTitle) {
            firstFieldRow = 0;
            return;
        }

        renderedConfiguredContent = true;

        add(createTitleHeader(viewable),
                GridBagUtils.weighted(
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
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true),
                    BorderFactory.createEmptyBorder(4, CollapseGutter.WIDTH, 4, 4)));
            armCollapseGutter(this::toggleCollapsed);
        }

        add(collapsibleRootHeader(expanded),
                GridBagUtils.weighted(
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
        String title = getTitle();
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
                captionKey());
        titleLabel.putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, title);
        titleLabel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY,
                path.append(captionKey()));

        if (renderContext.selectionEnabled()) {
            titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            titleLabel.setToolTipText(renderContext.multipleSelectionEnabled()
                    ? "Click to select · Ctrl/Cmd-click to toggle · Shift-click for a range"
                    : "Click to select");
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
        String title = getTitle();

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0, 80, 180));
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boolean hostActivation = rootRender && renderContext != null
                && renderContext.canActivate(q);
        titleLabel.setToolTipText(focusTopLevel
                ? "Click to focus existing panel"
                : hostActivation
                        ? "Double-click to activate"
                        : "Double-click to open full view");

        titleLabel.putClientProperty(FieldProperties.FIELD_NAME_PROPERTY,
                captionKey());
        titleLabel.putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, title);
        titleLabel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY,
                path.append(captionKey()));

        // A view can enable single-selection (e.g. curation, to pick the instance
        // to fill): a single click on the card's name selects it — the render
        // context tracks the one selected object, repaints the affected cards, and
        // notifies listeners. Double-click still opens the detail view below.
        if (renderContext != null && renderContext.selectionEnabled()) {
            // Only promise the gesture this view actually offers.
            String doubleClick = hostActivation ? "activate" : "open";
            titleLabel.setToolTipText(renderContext.multipleSelectionEnabled()
                    ? "Click to select · Ctrl/Cmd-click to toggle · Shift-click for a range · double-click to "
                            + doubleClick
                    : "Click to select — double-click to " + doubleClick);
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
        // CENTER receives only the width left after the trailing decoration. A title
        // in WEST keeps its full preferred width and can paint underneath an EAST QID
        // chip when a derived statement name is long.
        header.add(titleLabel, BorderLayout.CENTER);
        attachDecoration(header);

        return header;
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

    /** Lays out this object's field decisions. A scalar image is painted first, as the
     * object's avatar; plain values fold into one drag-selectable text block. Both are
     * layout only: which fields appear was decided by the executor. */
    private void buildFields() {
        int row = firstFieldRow;
        List<TextBlock.Row> textRows = new ArrayList<>();
        List<RenderExecutor.Decision> decisions =
                renderContext.executor().fields(level(), occurrence(), ancestors);
        List<RenderExecutor.Decision> ordered = new ArrayList<>();
        for (RenderExecutor.Decision decision : decisions) {
            if (isAvatar(decision)) ordered.add(decision);
        }
        for (RenderExecutor.Decision decision : decisions) {
            if (!isAvatar(decision)) ordered.add(decision);
        }

        for (RenderExecutor.Decision decision : ordered) {
            if (decision.kind() == RenderExecutor.Kind.SKIP) continue;
            FieldPath fieldPath = decision.at().path();
            String label = decision.field().label();

            if (decision.kind() == RenderExecutor.Kind.LEAF) {
                if (isTextBlockCandidate(decision.field().field(), decision.value())) {
                    textRows.add(textBlockRow(label, fieldPath, decision.value()));
                    continue;
                }
            }

            if (!textRows.isEmpty()) {
                row = addTextBlock(textRows, row);
                textRows.clear();
            }

            JComponent component = paint(decision, label, ancestors);
            if (component != null) {
                // Rendering uses the human label; search and config address the stable
                // machine key. Keep both on the resulting field component.
                component.putClientProperty(
                        FieldProperties.FIELD_NAME_PROPERTY, decision.field().name());
                component.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY, fieldPath);
                component.putClientProperty(
                        FieldProperties.FIELD_VALUE_PROPERTY, decision.value());
                addSingle(component, row++);
            }
        }

        if (!textRows.isEmpty()) {
            row = addTextBlock(textRows, row);
        }

        // Root cards only: pin fields to the top by absorbing any extra
        // card height in one zero-paint filler, instead of letting GridBag
        // centre the content (which left a variable gap). Nested panels are
        // content-sized, so they don't need it.
        if (path.isRoot()) {
            add(Box.createGlue(), GridBagUtils.weighted(
                    0, row + 1, 1.0, 1.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 0)));
        }
    }

    private static boolean isAvatar(RenderExecutor.Decision decision) {
        return decision.kind() == RenderExecutor.Kind.LEAF
                && decision.representation() == Representation.MEDIA;
    }

    private RenderSink.Occurrence occurrence() {
        return new RenderSink.Occurrence(path, "", path.dotted());
    }

    private int addTextBlock(List<TextBlock.Row> rows, int row) {
        TextBlock block = new TextBlock(rows);

        if (!block.isEmpty()) {
            addSingle(block, row++);
        }

        return row;
    }

    /** Paints one decision. {@code ancestors} are the objects on the path to it. */
    private JComponent paint(RenderExecutor.Decision decision, String label,
                             Set<Object> ancestors) {
        return painter.renderDecision(decision, new PaintContext(label, ancestors));
    }

    /** Layout-only information; all semantic branching stays in DecisionRenderer. */
    private record PaintContext(String label, Set<Object> ancestors) {}

    /** This card's side of the one decision dispatch, kept off its public API. */
    private final DecisionRenderer<JComponent, PaintContext> painter = new DecisionRenderer<>() {
        @Override public JComponent skip(RenderExecutor.Decision decision,
                                         PaintContext context) {
            return null;
        }

        @Override public JComponent leaf(RenderExecutor.Decision decision,
                                         PaintContext context) {
            return paintLeaf(decision, context.label());
        }

        @Override public JComponent navigation(RenderExecutor.Decision decision,
                                               PaintContext context) {
            RenderExecutor.Level object = decision.object();
            return maybeDecoratedReference(new ReferenceRow(
                    context.label(), decision.at().path(), object.target(), renderContext,
                    object.config(), objectPathTitle(object.target()), false, true,
                    object.caption() == null
                            ? ReferenceRow.NAVIGATION_LABEL : object.caption(),
                    object.captionField()), object.target(), true);
        }

        @Override public JComponent backReference(RenderExecutor.Decision decision,
                                                  PaintContext context) {
            return paintBackReference(decision, context.label());
        }

        @Override public JComponent object(RenderExecutor.Decision decision,
                                           PaintContext context) {
            return paintObject(decision, context.label(), context.ancestors());
        }

        @Override public JComponent collection(RenderExecutor.Decision decision,
                                               PaintContext context) {
            return paintCollection(decision, context.label(), context.ancestors());
        }
    };

    private JComponent paintLeaf(RenderExecutor.Decision decision, String label) {
        FieldPath fieldPath = decision.at().path();
        Object value = decision.value();
        if (decision.representation() == Representation.LINK
                && value instanceof String url && !url.isBlank()) {
            FieldRef field = decision.field() == null ? null : decision.field().field();
            return new LinkRow(label, fieldPath, url, field == null ? "" : field.linkText());
        }
        // Not declared a link, but the caller recognises what this value denotes. Keeps
        // the knowledge outside this module: objectview never learns what a QID is, and
        // every view that renders one gains the link at once.
        if (value instanceof String text && !text.isBlank()) {
            String resolved = renderContext.valueLink(text);
            if (resolved != null) return new LinkRow(label, fieldPath, resolved, text);
        }
        if (value instanceof ImagePane image && config.isBlurImages() && viewable != null) {
            value = blurForQuiz(image);
        }
        return ValueRenderer.leaf(label, fieldPath, value);
    }

    /** An object already on the path: a link to its card when it has one, else a
     * return mark. Named by its caption only when that is ticked. */
    private JComponent paintBackReference(RenderExecutor.Decision decision, String label) {
        RenderExecutor.Level object = decision.object();
        Viewable target = object.target();
        boolean hasCard = renderContext.isTopLevel(target);
        if (object.caption() == null && !hasCard) {
            return new TextRow(label, decision.at().path(), List.of("↩"));
        }
        return new ReferenceRow(label, decision.at().path(), target, renderContext,
                object.config(), objectPathTitle(target), false, hasCard,
                object.caption() == null ? ReferenceRow.NAVIGATION_LABEL : object.caption(),
                object.captionField());
    }

    /**
     * An object occurrence. With nothing ticked under it besides its caption it is a
     * value, not a door: its caption, or its field name alone (rule 3). With a caption
     * and a body, the caption is a chip that folds the body. With a body and no caption
     * there is nothing to fold with, so the body shows under the field name.
     */
    private JComponent paintObject(RenderExecutor.Decision decision, String label,
                                   Set<Object> ancestors) {
        RenderExecutor.Level object = decision.object();
        Viewable target = object.target();
        FieldPath fieldPath = decision.at().path();
        boolean decorate = decision.representation() == Representation.REFERENCE;

        if (!object.hasBody()) {
            if (object.caption() == null) {
                return label == null || label.isBlank()
                        ? null : new TextRow(label, fieldPath, List.of());
            }
            return maybeDecoratedReference(
                    new TextRow(label, fieldPath.append(object.captionField()),
                            object.caption()),
                    target, decorate);
        }

        // An inherited object folds even without a caption (#368): its field name is
        // then the chip that opens it, since a body shown at once would open the next
        // inherited level with it, and the next.
        boolean inherited = decision.field() != null && decision.field().inherited();
        if (object.caption() == null && !inherited) {
            JComponent body = nestedCard(object, fieldPath, ancestors);
            if (body == null) {
                return label == null || label.isBlank()
                        ? null : new TextRow(label, fieldPath, List.of());
            }
            return wrapObjectField(label, fieldPath, body);
        }

        boolean open = decision.open();
        boolean named = object.caption() == null;
        String chipText = !named ? object.caption()
                : label == null || label.isBlank() ? ReferenceRow.NAVIGATION_LABEL : label;
        ReferenceRow chip = new ReferenceRow(
                named ? "" : label, fieldPath, target, renderContext, object.config(),
                objectPathTitle(target), open, false,
                chipText, object.captionField());
        if (!open) {
            return maybeDecoratedReference(chip, target, decorate);
        }

        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(maybeDecoratedReference(chip, target, decorate), GridBagUtils.weighted(
                0, 0, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 0)));

        JComponent inline = nestedCard(object, fieldPath, ancestors);
        if (inline != null) {
            // A nested expansion collapses from its own left edge too. Its body is the
            // one that gets long — an expanded wbgetentities group runs for hundreds of
            // rows — and the chip that opened it scrolls off exactly like a card header.
            int indent = 16;
            if (inline instanceof Card body) {
                body.setBorder(BorderFactory.createEmptyBorder(
                        0, CollapseGutter.WIDTH, 0, 0));
                body.armCollapseGutter(chip::collapse);
                // The strip stands IN the indent rather than adding to it, so nesting
                // does not drift further right with every level that gains one.
                indent -= CollapseGutter.WIDTH;
            }
            wrap.add(inline, GridBagUtils.weighted(
                    0, 1, 1.0, 0.0,
                    GridBagConstraints.NORTHWEST,
                    GridBagConstraints.HORIZONTAL,
                    new Insets(0, indent, 4, 0)));
        }
        return wrap;
    }

    /** The body of an open object: a nested card over its exact literal child config.
     * The caption above already names it, so the card has no title of its own. */
    private JComponent nestedCard(RenderExecutor.Level object, FieldPath fieldPath,
                                  Set<Object> ancestors) {
        Set<Object> path = identitySetOf();
        path.addAll(ancestors);
        Card nested = new Card(identitySetOf(), path, renderContext, false,
                object.target(), object.config(), fill, fieldPath, null, null,
                true, false);
        return nested.hasRenderedConfiguredContent() ? nested : null;
    }

    /** {@code label (size)} at once; members only when open. */
    private JComponent paintCollection(RenderExecutor.Decision decision, String label,
                                       Set<Object> ancestors) {
        Object value = decision.value();
        Set<Object> path = identitySetOf();
        path.addAll(ancestors);
        return CollapsibleFieldRenderer.create(
                label, decision.at().path(), value, value, decision.size(),
                decision.open(), Disclosure.initiallyOpen(decision.field(), value),
                renderContext, () -> collectionBody(decision, path));
    }

    private JComponent collectionBody(RenderExecutor.Decision decision, Set<Object> ancestors) {
        FieldPath fieldPath = decision.at().path();
        Object value = decision.value();
        if (value instanceof Map<?, ?> map) {
            JComponent plain = ValueRenderer.plainMap("", fieldPath, map);
            return plain != null ? plain : mapBody(decision, map, ancestors);
        }
        Collection<?> items = RenderExecutor.members(value);
        JComponent plain = ValueRenderer.plainCollection("", fieldPath, items);
        return plain != null ? plain : memberPanel(decision.field(), decision.at(), items, ancestors);
    }

    /** One member, decided by the executor and painted like any field value. */
    private JComponent paintMember(ObjectPlan.FieldPlan collection,
                                   RenderSink.Occurrence at, Object item, int index,
                                   Set<Object> ancestors) {
        RenderExecutor.Decision member = renderContext.executor().member(
                collection, item, at.member(index), ancestors);
        return paint(member, "", ancestors);
    }

    private JComponent mapBody(RenderExecutor.Decision decision, Map<?, ?> map,
                               Set<Object> ancestors) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        int row = 0;
        for (Map.Entry<?, ?> entry : RenderSnapshot.map(map).entrySet()) {
            JComponent value = paintMember(decision.field(), decision.at(),
                    entry.getValue(), row, ancestors);
            if (value == null) continue;
            JPanel entryPanel = new JPanel(new BorderLayout(6, 0));
            entryPanel.setOpaque(false);
            entryPanel.add(mapKey(decision.at().path(), entry.getKey()), BorderLayout.WEST);
            entryPanel.add(value, BorderLayout.CENTER);
            panel.add(entryPanel, GridBagUtils.weighted(0, row++, 1.0, 0.0,
                    GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                    new Insets(2, 2, 2, 2)));
        }
        return row == 0 ? null : panel;
    }

    /** A map key: the link or picture it denotes, else bold text. */
    private static JComponent mapKey(FieldPath fieldPath, Object key) {
        if (ValueRenderer.rendersAsUrl(key)) {
            JComponent url = ValueRenderer.leaf("", fieldPath, key);
            if (url != null) return url;
        }
        JLabel label = new JLabel(String.valueOf(key));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    /** The members of an open collection: one component each, or a virtual viewport
     * when there are too many. The panel keeps what live updates need to paint later
     * members exactly the same way. */
    private JComponent memberPanel(ObjectPlan.FieldPlan collection,
                                   RenderSink.Occurrence at, Collection<?> items,
                                   Set<Object> ancestors) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.putClientProperty(INLINE_ITEMS, items);
        panel.putClientProperty(INLINE_FIELD_PATH, at.path());
        panel.putClientProperty(INLINE_FIELD_PLAN, collection);
        panel.putClientProperty(INLINE_OCCURRENCE, at);
        panel.putClientProperty(INLINE_ANCESTORS, ancestors);
        java.util.IdentityHashMap<Viewable, JComponent> rendered =
                new java.util.IdentityHashMap<>();
        panel.putClientProperty(INLINE_RENDERED, rendered);
        panel.putClientProperty(INLINE_ITEM_COUNT, items.size());

        List<Viewable> objects = new ArrayList<>();
        for (Object item : items) if (item instanceof Viewable value) objects.add(value);
        if (objects.size() > INLINE_VIRTUALIZATION_THRESHOLD && objects.size() == items.size()) {
            installVirtualInlineCollection(panel, objects, collection, at, ancestors);
            return panel;
        }

        int index = 0;
        for (Object item : items) {
            JComponent member = paintMember(collection, at, item, index++, ancestors);
            if (member == null) continue;
            addInlineItem(panel, member, panel.getComponentCount());
            if (item instanceof Viewable value) rendered.put(value, member);
        }
        return panel.getComponentCount() == 0 ? null : panel;
    }

    private VirtualizedCardList installVirtualInlineCollection(
            JPanel panel,
            List<Viewable> values,
            ObjectPlan.FieldPlan collection,
            RenderSink.Occurrence at,
            Set<Object> ancestors) {
        // An expanded workflow can contain tens of thousands of steps. The
        // enclosing CardListView virtualizes top-level cards, but that cannot
        // help a single card whose inline collection eagerly creates one Swing
        // component per member. Give the shared inline collection its own
        // viewport so only the visible members (and their opened bodies) exist.
        VirtualizedCardList[] holder = new VirtualizedCardList[1];
        holder[0] = new VirtualizedCardList(q ->
                new InlineVirtualRow(q, holder[0], collection, at, ancestors));
        VirtualizedCardList virtual = holder[0];
        JScrollPane scroll = new JScrollPane();
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        virtual.install(scroll);
        virtual.setItems(values);
        scroll.setPreferredSize(new Dimension(1, INLINE_VIRTUAL_HEIGHT));
        scroll.setMinimumSize(new Dimension(1, 160));
        panel.putClientProperty(INLINE_VIRTUAL_LIST, virtual);
        panel.add(scroll, GridBagUtils.weighted(
                0, 0, 1.0, 1.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.BOTH,
                new Insets(2, 6, 2, 6)));
        return virtual;
    }

    /**
     * A virtual inline row is the refresh boundary for its own disclosure chip.
     * Expanding one row therefore rematerializes that row, not the 14,000-member
     * containing card, and the nested viewport keeps its scroll position.
     */
    private final class InlineVirtualRow extends JPanel implements RenderRefreshHost {
        private final Viewable target;
        private final VirtualizedCardList owner;

        private InlineVirtualRow(
                Viewable target,
                VirtualizedCardList owner,
                ObjectPlan.FieldPlan collection,
                RenderSink.Occurrence at,
                Set<Object> ancestors) {
            super(new BorderLayout());
            this.target = target;
            this.owner = owner;
            setOpaque(false);
            putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, target);
            int index = owner == null ? 0 : Math.max(0, owner.items().indexOf(target));
            JComponent rendered = paintMember(collection, at, target, index, ancestors);
            if (rendered != null) add(rendered, BorderLayout.CENTER);
        }

        @Override
        public void refreshRenderedContent() {
            owner.invalidateCard(target);
        }
    }

    private static void addInlineItem(JPanel panel, JComponent nested, int row) {
        panel.add(nested, GridBagUtils.weighted(
                0, row, 1.0, 0.0,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                new Insets(2, 6, 2, 6)));
    }

    /** Updates mutable inline-collection counts without rebuilding the card. */
    public void refreshInlineCollectionCounts() {
        refreshInlineCollectionCounts(this);
        refreshCollectionHeaderCounts(this);
    }

    /**
     * Applies nested value mutations without rebuilding this card. New collection
     * members are appended; only already-rendered changed members are replaced.
     * Collapsed branches stay lazy and therefore cost nothing until opened.
     */
    public void updateInlineCollections(Collection<? extends Viewable> changed) {
        java.util.IdentityHashMap<Viewable, Boolean> changedSet =
                new java.util.IdentityHashMap<>();
        if (changed != null) {
            for (Viewable value : changed) {
                if (value != null) changedSet.put(value, Boolean.TRUE);
            }
        }
        updateInlineCollections(this, changedSet);
        refreshCollectionHeaderCounts(this);
        revalidate();
        repaint();
    }

    @SuppressWarnings("unchecked")
    private void updateInlineCollections(
            Container parent, java.util.IdentityHashMap<Viewable, Boolean> changed) {
        for (Component component : parent.getComponents()) {
            Component descendInto = component;
            if (component instanceof JPanel panel
                    && panel.getClientProperty(INLINE_ITEMS) instanceof Collection<?> items) {
                Object stored = panel.getClientProperty(INLINE_RENDERED);
                java.util.IdentityHashMap<Viewable, JComponent> rendered =
                        stored instanceof java.util.IdentityHashMap<?, ?> map
                                ? (java.util.IdentityHashMap<Viewable, JComponent>) map
                                : new java.util.IdentityHashMap<>();
                panel.putClientProperty(INLINE_RENDERED, rendered);
                FieldPath fieldPath = panel.getClientProperty(INLINE_FIELD_PATH)
                        instanceof FieldPath value ? value : path;
                if (!(panel.getClientProperty(INLINE_FIELD_PLAN)
                        instanceof ObjectPlan.FieldPlan collection)
                        || !(panel.getClientProperty(INLINE_OCCURRENCE)
                        instanceof RenderSink.Occurrence at)) {
                    continue;
                }
                Set<Object> pathObjects = panel.getClientProperty(INLINE_ANCESTORS)
                        instanceof Set<?> held ? (Set<Object>) held : identitySetOf();
                VirtualizedCardList virtual =
                        panel.getClientProperty(INLINE_VIRTUAL_LIST)
                                instanceof VirtualizedCardList value ? value : null;
                java.util.IdentityHashMap<Viewable, Boolean> newlyAdded =
                        new java.util.IdentityHashMap<>();

                int previousCount = panel.getClientProperty(INLINE_ITEM_COUNT)
                        instanceof Integer count ? count : -1;
                if (items.size() != previousCount) {
                    if (virtual == null
                            && items.size() > INLINE_VIRTUALIZATION_THRESHOLD) {
                        List<Viewable> current = new ArrayList<>();
                        for (Object item : items) {
                            if (item instanceof Viewable value) current.add(value);
                        }
                        if (current.size() > INLINE_VIRTUALIZATION_THRESHOLD) {
                            panel.removeAll();
                            rendered.clear();
                            virtual = installVirtualInlineCollection(
                                    panel, current, collection, at, pathObjects);
                            panel.putClientProperty(INLINE_ITEM_COUNT, items.size());
                        }
                    }
                    // Live logs append. For a List, visit only the new suffix; do
                    // not rediscover the thousands of members already rendered.
                    Iterable<?> candidates = items instanceof List<?> list
                            && previousCount >= 0 && items.size() > previousCount
                            ? list.subList(previousCount, list.size())
                            : items;
                    List<Viewable> virtualAdditions = new ArrayList<>();
                    for (Object item : candidates) {
                        if (!(item instanceof Viewable value)
                                || rendered.containsKey(value)
                                || virtual != null && virtual.containsItem(value)) {
                            continue;
                        }
                        if (virtual != null) {
                            virtualAdditions.add(value);
                            newlyAdded.put(value, Boolean.TRUE);
                        } else {
                            JComponent added = paintMember(
                                    collection, at, value, rendered.size(), pathObjects);
                            if (added != null) {
                                addInlineItem(panel, added, rendered.size());
                                rendered.put(value, added);
                                newlyAdded.put(value, Boolean.TRUE);
                            }
                        }
                    }
                    if (virtual != null) virtual.appendItems(virtualAdditions);
                    panel.putClientProperty(INLINE_ITEM_COUNT, items.size());
                }

                for (Viewable value : changed.keySet()) {
                    if (newlyAdded.containsKey(value)) continue;
                    if (virtual != null) {
                        virtual.invalidateCard(value);
                        continue;
                    }
                    JComponent old = rendered.get(value);
                    if (old != null) {
                        GridBagConstraints constraints =
                                ((GridBagLayout) panel.getLayout()).getConstraints(old);
                        int position = panel.getComponentZOrder(old);
                        JComponent replacement = paintMember(
                                collection, at, value, position, pathObjects);
                        if (replacement != null) {
                            panel.remove(old);
                            panel.add(replacement, constraints, position);
                            rendered.put(value, replacement);
                            descendInto = replacement;
                        }
                    }
                }
                refreshInlineCollectionCount(panel);
                panel.revalidate();
                panel.repaint();
            }
            if (descendInto instanceof Container nested
                    && nested.getComponentCount() > 0) {
                updateInlineCollections(nested, changed);
            }
        }
    }

    /**
     * The header says how many members the collection HAS, which is the question a
     * reader is asking of it — not how many produced a component. The two differ when
     * a member renders as nothing, and the count would then change on a refresh while
     * the collection had not.
     */
    private static int inlineViewableCount(Object items) {
        if (!(items instanceof Collection<?> values)) return 0;
        int count = 0;
        for (Object item : values) if (item instanceof Viewable) count++;
        return count;
    }

    private static void refreshInlineCollectionCount(JComponent panel) {
        if (panel.getClientProperty(INLINE_TITLE) instanceof String title
                && panel.getBorder() instanceof javax.swing.border.TitledBorder border) {
            String updated = title + " ("
                    + inlineViewableCount(panel.getClientProperty(INLINE_ITEMS)) + ")";
            if (!updated.equals(border.getTitle())) {
                border.setTitle(updated);
                panel.repaint();
            }
        }
    }

    private static void refreshInlineCollectionCounts(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JComponent jc) {
                refreshInlineCollectionCount(jc);
            }
            if (component instanceof Container nested) {
                refreshInlineCollectionCounts(nested);
            }
        }
    }

    private static void refreshCollectionHeaderCounts(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof CollectionHeader header) {
                header.refreshCount();
            }
            if (component instanceof Container nested) {
                refreshCollectionHeaderCounts(nested);
            }
        }
    }

    private JComponent maybeDecoratedReference(
            JComponent row, Viewable target, boolean decorateIdentity) {
        return decorateIdentity ? decoratedReference(row, target) : row;
    }

    // Reuse the card-header identity decorator (e.g. TransformApp's clickable QID chip) on a
    // reference chip too, so a referenced entity surfaces its identity the SAME way a card
    // does — no bespoke identity rendering. Scoped to a non-null decoration, so a plain
    // reference (identity not actionable) stays a plain chip.
    /** Give a captionless object field its own visible field name while leaving the
     * selected child fields untouched beneath it. */
    private static JComponent wrapObjectField(
            String fieldName, FieldPath fieldPath, JComponent body) {
        if (body == null || fieldName == null || fieldName.isBlank()) return body;
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(fieldName));
        panel.putClientProperty(FieldProperties.FIELD_NAME_PROPERTY, fieldName);
        panel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY, fieldPath);
        panel.add(body, BorderLayout.CENTER);
        return panel;
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
        // than folding into the (drag-to-select) text block. A value the CALLER
        // recognises leaves the block for the same reason — the batch is painted text,
        // and a link has to be its own clickable row to be reachable at all.
        if (renderContext != null && value instanceof String recognised
                && !recognised.isBlank()
                && renderContext.valueLink(recognised) != null) {
            return false;
        }
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

        // A value that denotes something else — a URL, or an image's address — renders as
        // the link or the picture (see ValueRenderer.rendersAsUrl). Folded into the
        // painted block it would be neither, so a card would show as plain text what the
        // same value shows as an image one view-mode away.
        if (ValueRenderer.rendersAsUrl(value)) {
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

    private Set<Object> copyAncestors() {
        Set<Object> copy = identitySetOf();
        copy.addAll(ancestors);
        return copy;
    }

    private void addSingle(Component comp, int row) {
        renderedConfiguredContent = true;

        add(comp, GridBagUtils.weighted(
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
                    if (!(rootRender && renderContext.activate(q))) openInFrame(q);
                }
            }
        });
    }

    private final java.awt.event.MouseAdapter cardSelectionListener =
            new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent event) {
                    if (!rootRender || renderContext == null
                            || !renderContext.selectionEnabled()
                            || !SwingUtilities.isLeftMouseButton(event)
                            || event.getClickCount() != 1
                            || isIndependentAction(event)) {
                        return;
                    }
                    // Cards are the list rows. Give the clicked row keyboard focus so
                    // the enclosing SearchableView's Cmd/Ctrl-A binding wins over a
                    // stale focus left in the search text field.
                    Card.this.setFocusable(true);
                    Card.this.requestFocusInWindow();
                    renderContext.select(viewable,
                            event.isControlDown() || event.isMetaDown(), event.isShiftDown());
                }
            };

    /** Swing mouse events do not bubble. Install one shared listener across the root
     * card, while leaving the actual painted hyperlink and other controls to their
     * own actions. */
    private void installCardSelectionListeners(Component component) {
        if (!rootRender || renderContext == null || !renderContext.selectionEnabled()) {
            return;
        }
        if (component instanceof JComponent jc
                && !Boolean.TRUE.equals(jc.getClientProperty("objectview.cardSelection"))) {
            jc.putClientProperty("objectview.cardSelection", Boolean.TRUE);
            component.addMouseListener(cardSelectionListener);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installCardSelectionListeners(child);
            }
        }
    }

    private static boolean isIndependentAction(java.awt.event.MouseEvent event) {
        Component source = event.getComponent();
        if (source instanceof LinkRow link && link.isPointOverValue(event.getPoint())) {
            return true;
        }
        return source instanceof AbstractButton
                || source instanceof ReferenceRow
                || source instanceof CollectionHeader
                || source instanceof objectview.media.ImagePane;
    }

    private void openInFrame(Viewable q) {
        new CardFrame(q,
                      ViewConfig.allWithMinorFields(q.getClass())
                                    .setAddListener(config.isAddListener())
                                    .setThumb(config.isThumb()),
                      renderContext.detachedDetailContext());
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
        String caption = level().caption();
        return caption == null ? "" : caption;
    }

    /** The DISPLAY field the title paints, for search and highlighting. */
    private String captionKey() {
        String field = level().captionField();
        return field == null ? objectview.field.ViewableContractFieldSet.DISPLAY_KEY : field;
    }
}
