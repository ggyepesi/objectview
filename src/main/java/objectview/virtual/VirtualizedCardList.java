package objectview.virtual;

import objectview.Viewable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A vertically virtualized list of Viewable cards.
 *
 * <p>The list owns:
 * <ul>
 *   <li>the ordered Viewable items,</li>
 *   <li>the currently materialized card components,</li>
 *   <li>measured and estimated card heights,</li>
 *   <li>viewport navigation and lazy card creation.</li>
 * </ul>
 *
 * <p>The list does not know how a card is configured or rendered. Card creation
 * is delegated to {@code cardFactory}. This allows callers such as grouped views
 * to supply their own ViewConfig and RenderContext.
 */
public final class VirtualizedCardList
        extends JComponent
        implements Scrollable, VirtualizedContainer {

    private static final Logger log = LoggerFactory.getLogger(VirtualizedCardList.class);


    private static final int BUFFER = 6;
    private static final int DEFAULT_ROW = 140;

    /**
     * Below this width, cards stop shrinking and horizontal scrolling is used
     * instead.
     */
    private static final int MIN_CONTENT_WIDTH = 380;

    private static final int MAX_ESTIMATE_SAMPLES = 10;

    private static final boolean DEBUG =
            Boolean.getBoolean("quiz.nav.debug");

    /**
     * Mutable so the owner can apply a new view configuration by replacing the
     * factory. Existing built cards are then discarded and rebuilt lazily.
     */
    private Function<Viewable, JComponent> cardFactory;

    // Notified with each card as it's (re)materialized on scroll, so an owner can
    // re-apply transient decoration a fresh card would otherwise lack — e.g. the
    // search highlight, which is lost when a card is virtualized out and rebuilt.
    private java.util.function.Consumer<JComponent> onCardBuilt;

    // Invoked at the start of navigateToTop, so the owner can reveal the target
    // (e.g. expand a collapsed card whose search-hit field is hidden) before it
    // is built and scrolled into view.
    private java.util.function.Consumer<Viewable> onNavigateReveal;

    private List<Viewable> items = new ArrayList<>();

    private final Map<Viewable, JComponent> built =
            new IdentityHashMap<>();

    /**
     * Exact currently measured height per object.
     */
    private final Map<Viewable, Integer> heights =
            new IdentityHashMap<>();

    private final Map<Viewable, Integer> indexByItem =
            new IdentityHashMap<>();

    /**
     * Class-level height estimates for cards that have not yet been built.
     */
    private final Map<Class<?>, HeightEstimate> estimatesByClass =
            new HashMap<>();

    /**
     * {@code tops[i]} is the vertical position of item {@code i}.
     * {@code tops[items.size()]} is the bottom of the final item.
     */
    private int[] tops = {0};

    private JViewport viewport;
    private boolean updating;
    private int navGeneration;

    public VirtualizedCardList(
            Function<Viewable, JComponent> cardFactory) {

        this.cardFactory = Objects.requireNonNull(
                cardFactory,
                "cardFactory"
                                                 );

        setLayout(null);
    }

    /**
     * Replaces the card builder while preserving the item list.
     *
     * <p>All currently materialized cards and height measurements are discarded.
     * Cards are recreated lazily when they next enter the visible range.
     */
    public void setCardFactory(
            Function<Viewable, JComponent> cardFactory) {

        this.cardFactory = Objects.requireNonNull(
                cardFactory,
                "cardFactory"
                                                 );

        discardBuiltCards();
    }

    private void discardBuiltCards() {
        removeAll();
        built.clear();

        /*
         * A changed view configuration can change wrapping, nested fields and
         * image sizes, so previous measurements are no longer reliable.
         */
        heights.clear();
        estimatesByClass.clear();

        rebuildTops();

        revalidate();
        repaint();
        updateVisible();
    }

    public void install(JScrollPane scroll) {
        Objects.requireNonNull(scroll, "scroll");

        scroll.setViewportView(this);
        viewport = scroll.getViewport();

        viewport.addChangeListener(e -> updateVisible());

        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getVerticalScrollBar().setBlockIncrement(200);
    }

    @Override
    public void setItems(List<Viewable> newItems) {
        items = new ArrayList<>(
                newItems == null ? List.of() : newItems
        );

        reindex();

        removeAll();
        built.clear();

        heights.keySet().removeIf(
                q -> !indexByItem.containsKey(q)
                                 );

        rebuildTops();

        revalidate();
        repaint();
        updateVisible();
    }

    public void appendItem(Viewable q) {
        if (q == null) {
            return;
        }

        items.add(q);
        indexByItem.put(q, items.size() - 1);

        rebuildTops();
        revalidate();
        updateVisible();
    }

    private void reindex() {
        indexByItem.clear();

        for (int i = 0; i < items.size(); i++) {
            indexByItem.put(items.get(i), i);
        }
    }

    @Override
    public List<Viewable> items() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public Viewable topVisibleItem() {
        if (viewport == null || items.isEmpty()) {
            return null;
        }

        int idx = indexAt(viewport.getViewPosition().y);

        return items.get(
                Math.max(0, Math.min(items.size() - 1, idx))
                        );
    }

    public JComponent builtCard(Viewable q) {
        return built.get(q);
    }

    /**
     * Builds and positions a card without scrolling to it.
     */
    public JComponent buildIfNeeded(Viewable q) {
        int i = indexOf(q);

        if (i < 0) {
            return null;
        }

        JComponent card = built.get(q);

        if (card == null) {
            card = buildCard(q);
        }

        positionCard(i, card);

        if (measureCardIfChanged(q, card)) {
            rebuildTops();
            positionCard(i, card);
            revalidate();
        }

        return card;
    }

    /**
     * Builds the card and scrolls enough to make it visible.
     */
    public JComponent ensureVisible(Viewable q) {
        int i = indexOf(q);

        if (i < 0) {
            return null;
        }

        buildIfNeeded(q);

        // Force the enclosing scroll pane to lay out NOW so this view is already
        // resized to its new (taller) preferred height. Without it, scrolling toward
        // a just-expanded LAST card stops at the stale content bottom and the
        // expanded body stays clipped until an unrelated relayout (e.g. a new entry).
        JScrollPane sp = (JScrollPane)
                SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (sp != null) {
            sp.validate();
        }

        scrollRectToVisible(new Rectangle(
                0,
                tops[i],
                1,
                rowHeight(q)
        ));

        updateVisible();

        return built.get(q);
    }

    /**
     * Builds the card and attempts to pin its top to the viewport top.
     */
    @Override
    public JComponent navigateToTop(Viewable q) {
        int i = indexOf(q);

        if (i < 0 || viewport == null) {
            return null;
        }

        // Let the owner REVEAL the target before it's built + scrolled to — e.g.
        // expand a collapsed card so a search hit on a hidden field becomes
        // visible. Mirrors VirtualizedGroupTreeView, which expands collapsed
        // ancestors in its own navigateToTop. The handler may rebuild this card
        // (changing its height), which the rebuildTops/scroll below then honours.
        if (onNavigateReveal != null) {
            onNavigateReveal.accept(q);
        }

        navGeneration++;

        if (DEBUG) {
            log.debug(
                    "[nav] start q={} i={} top={} total={} extent={}",
                    q.getDisplayName(),
                    i,
                    tops[i],
                    totalHeight(),
                    viewport.getExtentSize().height);
        }

        buildIfNeeded(q);

        /*
         * Measurement can shift all later card tops, so pin twice before the
         * deferred final drift correction.
         */
        rebuildTops();
        scrollIndexToTop(i);
        updateVisible();

        rebuildTops();
        scrollIndexToTop(i);
        updateVisible();

        int generation = navGeneration;

        SwingUtilities.invokeLater(() -> {
            if (generation == navGeneration) {
                repinIfDrifted(q, i);
            }
        });

        if (DEBUG) {
            JComponent card = built.get(q);

            log.debug(
                    "[nav] after q={} viewY={} cardY={} cardH={} top={}",
                    q.getDisplayName(),
                    viewport.getViewPosition().y,
                    card != null ? card.getY() : -1,
                    card != null ? card.getHeight() : -1,
                    tops[i]);
        }

        return built.get(q);
    }

    private void repinIfDrifted(
            Viewable q,
            int expectedIndex) {

        if (viewport == null || indexOf(q) != expectedIndex) {
            return;
        }

        rebuildTops();

        int wantY = clampTop(expectedIndex);
        int haveY = viewport.getViewPosition().y;

        if (Math.abs(haveY - wantY) > 1) {
            scrollIndexToTop(expectedIndex);
            updateVisible();
        }
    }

    private void scrollIndexToTop(int i) {
        setSize(
                Math.max(getWidth(), effectiveWidth()),
                preferredContentHeight()
               );

        viewport.setViewPosition(
                new Point(0, clampTop(i))
                                );
    }

    private int clampTop(int i) {
        return Math.max(
                0,
                Math.min(tops[i], maxScrollY())
                       );
    }

    /**
     * Furthest valid vertical scroll position.
     *
     * <p>It must support both:
     * <ul>
     *   <li>pinning the last card's top to the viewport top, and</li>
     *   <li>reaching the bottom of a final card taller than the viewport.</li>
     * </ul>
     */
    private int maxScrollY() {
        if (items.isEmpty()) {
            return 0;
        }

        int extent = viewport != null
                ? viewport.getExtentSize().height
                : 0;

        int lastTop = tops[items.size() - 1];

        return Math.max(
                lastTop,
                Math.max(0, contentHeight() - extent)
                       );
    }

    private int contentHeight() {
        return items.isEmpty()
                ? 0
                : tops[items.size()];
    }

    private int preferredContentHeight() {
        int extent = viewport != null
                ? viewport.getExtentSize().height
                : 0;

        return maxScrollY() + extent;
    }

    int topOf(Viewable q) {
        int i = indexOf(q);
        return i < 0 ? -1 : tops[i];
    }

    int totalHeight() {
        return tops.length == 0
                ? 0
                : tops[tops.length - 1];
    }

    private int indexOf(Viewable q) {
        Integer i = indexByItem.get(q);
        return i == null ? -1 : i;
    }

    private int rowHeight(Viewable q) {
        Integer exact = heights.get(q);

        if (exact != null) {
            return exact;
        }

        if (q != null) {
            HeightEstimate estimate =
                    estimatesByClass.get(q.getClass());

            if (estimate != null) {
                return estimate.value();
            }
        }

        return DEFAULT_ROW;
    }

    private int effectiveWidth() {
        int viewportWidth =
                viewport != null && viewport.getWidth() > 0
                        ? viewport.getWidth()
                        : Math.max(1, getWidth());

        return Math.max(
                viewportWidth,
                MIN_CONTENT_WIDTH
                       );
    }

    private void positionCard(
            int index,
            JComponent card) {

        Viewable q = items.get(index);

        card.setBounds(
                0,
                tops[index],
                effectiveWidth(),
                rowHeight(q)
                      );
    }

    /**
     * Lays out the whole card subtree at its current width before measuring its
     * preferred height. This is needed for wrapped text.
     */
    private static void layoutTree(java.awt.Container container) {
        container.doLayout();

        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container childContainer) {
                layoutTree(childContainer);
            }
        }
    }

    private boolean measureCardIfChanged(
            Viewable q,
            JComponent card) {

        if (q == null || card == null) {
            return false;
        }

        card.setSize(
                effectiveWidth(),
                card.getHeight()
                    );

        layoutTree(card);

        int measured = Math.max(
                1,
                card.getPreferredSize().height
                               );

        Integer known = heights.get(q);

        if (known != null && known == measured) {
            return false;
        }

        heights.put(q, measured);
        updateClassEstimate(q, measured, known);

        return true;
    }

    private void updateClassEstimate(
            Viewable q,
            int measured,
            Integer previousExactHeight) {

        if (q == null) {
            return;
        }

        HeightEstimate estimate =
                estimatesByClass.computeIfAbsent(
                        q.getClass(),
                        ignored -> new HeightEstimate()
                                                );

        /*
         * Once a card has already been measured, a dramatic growth is probably
         * caused by expansion and should not distort the class fallback.
         */
        if (previousExactHeight != null) {
            int oldEstimate = estimate.value();

            if (measured > oldEstimate * 2) {
                return;
            }
        }

        estimate.addSample(measured);
    }

    private void rebuildTops() {
        tops = new int[items.size() + 1];

        int y = 0;

        for (int i = 0; i < items.size(); i++) {
            tops[i] = y;
            y += rowHeight(items.get(i));
        }

        tops[items.size()] = y;
    }

    private int indexAt(int y) {
        if (items.isEmpty()) {
            return 0;
        }

        int low = 0;
        int high = items.size() - 1;

        while (low < high) {
            int middle = (low + high + 1) >>> 1;

            if (tops[middle] <= y) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        return low;
    }

    private void updateVisible() {
        if (viewport == null || items.isEmpty() || updating) {
            return;
        }

        updating = true;

        try {
            for (int pass = 0;
                 pass < 8 && updateVisibleOnce();
                 pass++) {
                // Allow height/layout changes to settle.
            }
        } finally {
            updating = false;
        }

        repaint();
    }

    /**
     * Performs one visible-range/materialization/layout pass.
     *
     * @return true when another pass may be required
     */
    private boolean updateVisibleOnce() {
        Rectangle view = viewport.getViewRect();

        int first = Math.max(
                0,
                indexAt(view.y) - BUFFER
                            );

        int last = Math.min(
                items.size() - 1,
                indexAt(view.y + view.height) + BUFFER
                           );

        int anchorIndex = indexAt(view.y);
        int withinAnchor = view.y - tops[anchorIndex];

        syncBuiltRange(first, last);

        boolean heightsChanged = false;

        for (int i = first; i <= last; i++) {
            Viewable q = items.get(i);
            JComponent card = built.get(q);

            if (card == null) {
                card = buildCard(q);
            }

            positionCard(i, card);

            if (measureCardIfChanged(q, card)) {
                heightsChanged = true;
            }
        }

        if (heightsChanged) {
            rebuildTops();

            for (int i = first; i <= last; i++) {
                JComponent card =
                        built.get(items.get(i));

                if (card != null) {
                    positionCard(i, card);
                }
            }

            revalidate();
        }

        int newY = Math.max(
                0,
                tops[anchorIndex] + withinAnchor
                           );

        newY = Math.min(newY, maxScrollY());

        boolean moved = newY != view.y;

        if (moved) {
            viewport.setViewPosition(
                    new Point(view.x, newY)
                                    );
        }

        return heightsChanged || moved;
    }

    private JComponent buildCard(Viewable q) {
        JComponent card = cardFactory.apply(q);

        if (card == null) {
            throw new IllegalStateException(
                    "Card factory returned null for " + q
            );
        }

        built.put(q, card);
        add(card);

        if (onCardBuilt != null) {
            onCardBuilt.accept(card);
        }

        return card;
    }

    /** Registers a callback invoked with each card as it's (re)materialized. */
    public void setOnCardBuilt(java.util.function.Consumer<JComponent> onCardBuilt) {
        this.onCardBuilt = onCardBuilt;
    }

    /** Registers a handler invoked at the start of {@link #navigateToTop} so the
     *  owner can reveal the target (e.g. expand a collapsed card) before it's
     *  built and scrolled into view. */
    public void setNavigateRevealHandler(java.util.function.Consumer<Viewable> handler) {
        this.onNavigateReveal = handler;
    }

    /**
     * Keeps only cards in or near the visible range.
     */
    private void syncBuiltRange(
            int first,
            int last) {

        Set<Viewable> keep =
                Collections.newSetFromMap(
                        new IdentityHashMap<>()
                                         );

        for (int i = Math.max(0, first);
             i <= last && i < items.size();
             i++) {

            keep.add(items.get(i));
        }

        for (Iterator<Map.Entry<Viewable, JComponent>> iterator =
             built.entrySet().iterator();
             iterator.hasNext(); ) {

            Map.Entry<Viewable, JComponent> entry =
                    iterator.next();

            if (!keep.contains(entry.getKey())) {
                remove(entry.getValue());
                iterator.remove();
            }
        }
    }

    public String diagnostics() {
        StringBuilder sb = new StringBuilder();

        int total = totalHeight();

        int viewY = viewport != null
                ? viewport.getViewPosition().y
                : 0;

        int extent = viewport != null
                ? viewport.getExtentSize().height
                : 0;

        sb.append(String.format(
                "VirtualizedCardList: items=%d built=%d measured=%d "
                        + "classEstimates=%d%n"
                        + "  viewY=%d (%.1f%%) extent=%d total=%d "
                        + "maxScrollY=%d%n",
                items.size(),
                built.size(),
                heights.size(),
                estimatesByClass.size(),
                viewY,
                total > 0 ? 100.0 * viewY / total : 0,
                extent,
                total,
                maxScrollY()
                               ));

        List<Map.Entry<Viewable, JComponent>> byScreenY =
                new ArrayList<>(built.entrySet());

        byScreenY.sort(
                Comparator.comparingInt(
                        entry -> entry.getValue().getY()
                                       )
                      );

        int previousIndex = Integer.MIN_VALUE;

        for (Map.Entry<Viewable, JComponent> entry : byScreenY) {
            Viewable q = entry.getKey();
            JComponent card = entry.getValue();

            int index = indexOf(q);
            int trackedTop =
                    index >= 0 ? tops[index] : -1;

            String flags = "";

            if (card.getY() != trackedTop) {
                flags += " OFFSET_MISMATCH(screenY!=tracked)";
            }

            if (index <= previousIndex) {
                flags += " ORDER_BREAK(screen order != list order)";
            }

            previousIndex = index;

            sb.append(String.format(
                    "  screenY=%d h=%d | listIdx=%d trackedTop=%d "
                            + "| exact=%s | %s%s%n",
                    card.getY(),
                    card.getHeight(),
                    index,
                    trackedTop,
                    heights.containsKey(q),
                    q.getDisplayName(),
                    flags
                                   ));
        }

        sb.append("Class estimates:\n");

        for (Map.Entry<Class<?>, HeightEstimate> entry
                : estimatesByClass.entrySet()) {

            sb.append(String.format(
                    "  %s -> %d (%d samples)%n",
                    entry.getKey().getSimpleName(),
                    entry.getValue().value(),
                    entry.getValue().sampleCount()
                                   ));
        }

        return sb.toString();
    }

    /**
     * Discards and rematerializes the current virtual layout while preserving
     * the current items and card factory.
     */
    public void rebuild() {
        setItems(new ArrayList<>(items));
    }

    /**
     * Discards a single card (and its stale height) so it is rebuilt fresh by
     * the factory at its next visible pass — used when a card's own rendering
     * changed (e.g. collapse/expand), so the new size is re-measured rather than
     * grown in place. No-op if the card isn't built. Call on the EDT.
     */
    public void invalidateCard(Viewable q) {
        if (q == null) {
            return;
        }
        JComponent card = built.remove(q);
        if (card != null) {
            remove(card);
        }
        heights.remove(q);

        rebuildTops();
        revalidate();
        repaint();
        updateVisible();
    }

    @Override
    public void doLayout() {
        updateVisible();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(
                effectiveWidth(),
                preferredContentHeight()
        );
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction) {

        return 20;
    }

    @Override
    public int getScrollableBlockIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction) {

        return orientation == SwingConstants.VERTICAL
                ? Math.max(20, visibleRect.height - 20)
                : Math.max(20, visibleRect.width - 20);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return viewport == null
                || viewport.getWidth() >= MIN_CONTENT_WIDTH;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /**
     * Conservative median-based class height estimate.
     */
    static final class HeightEstimate {

        private final List<Integer> samples =
                new ArrayList<>();

        private int cached = DEFAULT_ROW;

        void addSample(int height) {
            if (height <= 0) {
                return;
            }

            if (samples.size() < MAX_ESTIMATE_SAMPLES) {
                samples.add(height);
                recompute();
                return;
            }

            /*
             * Expanded/outlier cards keep their exact height, but do not move the
             * class fallback substantially.
             */
            if (height <= cached * 2) {
                samples.remove(0);
                samples.add(height);
                recompute();
            }
        }

        int value() {
            return cached;
        }

        int sampleCount() {
            return samples.size();
        }

        private void recompute() {
            if (samples.isEmpty()) {
                cached = DEFAULT_ROW;
                return;
            }

            List<Integer> sorted =
                    new ArrayList<>(samples);

            Collections.sort(sorted);

            cached = sorted.get(sorted.size() / 2);
        }
    }
}