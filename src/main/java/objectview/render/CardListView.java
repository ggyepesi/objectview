package objectview.render;

import objectview.utils.swing.GridBagUtils;
import objectview.media.ImagePane;
import objectview.Viewable;
import objectview.text.TextSelectable;
import objectview.text.TextSelectionManager;
import objectview.virtual.VirtualizedCardList;
import objectview.viewconfig.ViewConfig;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CardListView {
    private JFrame frame = null;

    private final List<Viewable> viewables = new ArrayList<>();
    private final List<RawImageEntry> rawImageEntries = new ArrayList<>();

    private final List<JPanel> cards = new ArrayList<>();
    private final Map<String, JPanel> cardsByName = new TreeMap<>();

    private JScrollPane cardsScrollPane;
    private VirtualizedCardList virtualList;

    // Shared across the initial render and any live additions so cards
    // resolve cross-references and class configs consistently.
    private RenderContext context;

    // Column count and trailing glue filler, remembered so a live add can
    // place the next card.
    private int columns = 1;

    private final List<CardListener> targetListeners =
            new ArrayList<>();

    // Optional externally-supplied context, shared with other views so a
    // reference click can navigate to a card in a sibling view.
    private RenderContext sharedContext;
    private boolean inPlaceNavigation;

    public CardListView() {
    }

    /**
     * Shares an external render context across views. All views using the
     * same context can resolve references to each other's cards. The owner
     * must pre-register every object (via {@link RenderContext}) so
     * cross-references render as chips rather than embedded cards.
     */
    public void setRenderContext(RenderContext context) {
        this.sharedContext = context;
    }

    /**
     * When true, single-clicking a reference whose target is a card in this
     * (or a shared) context navigates to it instead of opening a frame.
     */
    public void setInPlaceNavigation(boolean inPlaceNavigation) {
        this.inPlaceNavigation = inPlaceNavigation;
    }

    /**
     * Registers a listener notified when cards are added live (after the
     * frame is showing). {@link #createFrame} registers the internal
     * search panel automatically.
     */
    public void addTargetListener(CardListener listener) {
        if (listener != null && !targetListeners.contains(listener)) {
            targetListeners.add(listener);
        }
    }

    public Map<String, JPanel> getCardsByName() {
        return cardsByName;
    }

    public void addViewable(Viewable viewable) {
        addViewable(viewable, true, true);
    }

    public void addViewable(Viewable viewable,
                            boolean addTitle,
                            boolean addListeners) {
        if (viewable != null) {
            viewables.add(viewable);
        }
    }

    public void addImagePane(String title, ImagePane imagePane) {
        if (imagePane != null) {
            rawImageEntries.add(new RawImageEntry(title, imagePane));
        }
    }

    public void createCardsPanel(int numColumns) {
        cards.clear();
        cardsByName.clear();
        columns = Math.max(1, numColumns);

        // Raw image entries (the enlarged-image view) are not Viewables, so they
        // can't go through the virtualized card list — give them their own panel.
        // Only ImagePane.showImageView uses this path, always without viewables.
        if (viewables.isEmpty() && !rawImageEntries.isEmpty()) {
            createRawImagePanel();
            return;
        }

        context = resolveContext();
        // Register every viewable as top-level up front, so a reference chip
        // navigates (isTopLevel is data-based, not panel-based) even to a card
        // that hasn't been built yet; the resolver builds it on demand.
        context.addTopLevels(viewables);

        // Virtualized: only the cards in (or near) the viewport are built. Scroll,
        // sort and re-layout are O(visible), not O(N), so it stays fast at tens of
        // thousands of cards.
        virtualList = new VirtualizedCardList(this::buildVirtualCard);
        // A card virtualized out and rebuilt on scroll-back is fresh — tell listeners
        // (the search panel) so they can re-apply a lost highlight.
        virtualList.setOnCardBuilt(card -> {
            if (card instanceof Card qp) {
                for (CardListener listener : targetListeners) {
                    listener.cardMaterialized(qp);
                }
            }
        });
        // One resolver per section; the shared context tries each, so a reference to
        // a card in ANY section resolves (not just the last section to register).
        context.addTopLevelResolver(o ->
                o instanceof Viewable q ? virtualList.buildIfNeeded(q) : null);
        // Collapsible cards toggle by rebuilding the one card fresh (factory-driven),
        // so it re-measures at its new size instead of growing in place.
        context.addCardToggleHandler(q -> {
            virtualList.invalidateCard(q);
            // On EXPAND, scroll the now-taller card fully into view. Deferred past the
            // invalidate's relayout so the content height (and the viewport's view
            // size) has grown first — otherwise the LAST card's expanded body extends
            // past the old bottom and the scroll can't reach it until something else
            // relayouts (e.g. a new entry). Collapse needs no reveal.
            if (context.collapsibleCards() && context.isCardExpanded(q, false)) {
                SwingUtilities.invokeLater(() -> virtualList.ensureVisible(q));
            }
        });
        // Navigating to a card (e.g. a search hit) reveals it: a collapsed target
        // expands so a hit on a hidden field becomes visible. State lives in the
        // context, so it stays expanded on scroll-away-and-back.
        virtualList.setNavigateRevealHandler(q -> {
            if (context.collapsibleCards() && !context.isCardExpanded(q, false)) {
                context.toggleCardExpanded(q, false);
                virtualList.invalidateCard(q);
            }
        });

        cardsScrollPane = new JScrollPane();
        cardsScrollPane.setDoubleBuffered(true);
        cardsScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        RepaintManager.currentManager(cardsScrollPane)
                .setDoubleBufferingEnabled(true);

        virtualList.install(cardsScrollPane);
        virtualList.setItems(new ArrayList<>(viewables));
    }

    // The enlarged-image view: one holder per raw ImagePane, filling the frame
    // (no virtualization — there is only ever a handful, usually one).
    private void createRawImagePanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        int row = 0;
        for (RawImageEntry entry : rawImageEntries) {
            JPanel holder = new JPanel(new GridBagLayout());
            holder.add(entry.imagePane,
                    GridBagUtils.gbc(
                            0, 0,
                            1.0, 1.0,
                            GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH));
            cards.add(holder);
            if (entry.title != null) {
                cardsByName.putIfAbsent(entry.title, holder);
            }
            panel.add(holder,
                    GridBagUtils.gbc(
                            0, row++,
                            1.0, 1.0,
                            GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH));
        }

        cardsScrollPane = new JScrollPane(panel);
        cardsScrollPane.getVerticalScrollBar().setUnitIncrement(20);
    }

    // Card factory for the virtualized list: build the card, register it, and
    // index it by name for getCardsByName.
    private javax.swing.JComponent buildVirtualCard(Viewable q) {
        Card panel = buildCard(q);
        String name = q.getName();
        if (name != null && !name.isEmpty()) {
            cardsByName.putIfAbsent(name, panel);
        }
        return panel;
    }

    public VirtualizedCardList getVirtualList() {
        return virtualList;
    }

    /** Re-render every currently-built card in place, so a change to shared
     *  render state (e.g. {@link RenderContext} bulk expand/collapse) shows
     *  immediately. Cards not yet built pick the state up when built on scroll. */
    public void refreshBuiltCards() {
        if (virtualList == null) {
            return;
        }
        for (Viewable v : viewables) {
            javax.swing.JComponent c = virtualList.builtCard(v);
            if (c instanceof Card card) {
                card.refresh();
            }
        }
    }

    private Card buildCard(Viewable q) {
        ViewConfig cfg =
                ViewConfig.all(q.getClass())
                          .setAddListener(true)
                          .setThumb(true);

        context.putClassConfig(q.getClass(), cfg);

        Card panel =
                new Card(q, cfg, context, false);

        context.registerTopLevel(q, panel);

        tuneCardSize(panel);

        return panel;
    }

    /**
     * Adds a card after the frame is already showing: builds it, drops it
     * into the next grid slot, and notifies target listeners (the search
     * panel) so it stays searchable. Call on the EDT.
     *
     * Falls back to a deferred add if the cards panel hasn't been built
     * yet, in which case the card appears on the first render.
     */
    public void addViewableLive(Viewable q) {
        if (q == null) {
            return;
        }

        viewables.add(q);

        if (virtualList == null) {
            return;
        }

        if (context == null) {
            context = resolveContext();
        }
        context.addTopLevel(q);

        virtualList.appendItem(q);

        // The card may not have been built yet (off-screen); notify with it if it
        // was, so the search panel can index it. A data-centric listener re-reads
        // the item list anyway.
        Card panel = virtualList.builtCard(q) instanceof Card p
                ? p : null;
        for (CardListener listener : targetListeners) {
            listener.cardsAdded(panel != null ? List.of(panel) : List.of());
        }
    }

    /**
     * Re-renders the card backing {@code q} in place after its fields
     * changed, and notifies target listeners (the search panel) so they
     * re-index / re-sort it. No-op if {@code q} has no card yet. Call on
     * the EDT.
     */
    public void refreshViewable(Viewable q) {
        if (q == null || virtualList == null) {
            return;
        }

        Card card = findCard(q);

        if (card == null) {
            return;   // not on screen — it rebuilds fresh when scrolled into view
        }

        if (ownsActiveSelection(card)) {
            // The user is mid-drag selecting text INSIDE this card (e.g. copying a
            // line from a running query-log entry). refresh() rebuilds the card's
            // components and would wipe that selection. Skip the redraw — the data
            // is unchanged visually anyway, and the card re-renders on its next
            // update once the selection is released.
            return;
        }

        card.refresh();

        virtualList.revalidate();
        virtualList.repaint();

        for (CardListener listener : targetListeners) {
            listener.cardsUpdated(List.of(card));
        }
    }

    /**
     * Refreshes {@code q}'s card if it already has one, otherwise adds it
     * live. Convenient for incremental feeds (e.g. a query log) that don't
     * track whether a given item has been rendered yet. Call on the EDT.
     */
    public void upsertViewable(Viewable q) {
        if (q == null || virtualList == null) {
            return;
        }

        boolean known = false;
        for (Viewable item : virtualList.items()) {
            if (item == q) {
                known = true;
                break;
            }
        }
        if (known) {
            refreshViewable(q);
        } else {
            addViewableLive(q);
        }
    }

    private RenderContext resolveContext() {
        RenderContext c =
                sharedContext != null
                        ? sharedContext
                        : new RenderContext(viewables);

        if (sharedContext != null) {
            c.addTopLevels(viewables);
        }

        if (inPlaceNavigation) {
            c.setInPlaceNavigation(true);
        }

        return c;
    }

    private Card findCard(Viewable q) {
        return virtualList != null && virtualList.builtCard(q) instanceof Card qp
                ? qp : null;
    }

    // True when the live (non-empty) text selection lives inside this card, so a
    // refresh would destroy the user's in-progress copy selection.
    private static boolean ownsActiveSelection(Component card) {
        if (card == null || !TextSelectionManager.hasActiveSelection()) {
            return false;
        }
        TextSelectable selectable = TextSelectionManager.current();
        return selectable instanceof Component c
                && SwingUtilities.isDescendingFrom(c, card);
    }

    public javax.swing.JComponent getCardsPanel() {
        return virtualList;
    }

    public JScrollPane getCardsScrollPane() {
        return cardsScrollPane;
    }

    public RenderContext getRenderContext() {
        return context;
    }

    private void createCardsBad() {
        RenderContext context =
                new RenderContext(viewables);

        // First pass: register class configs before rendering.
        for (Viewable q : viewables) {
            if (q == null) {
                continue;
            }

            ViewConfig cfg =
                    ViewConfig.all(q.getClass())
                              .setAddListener(true)
                              .setThumb(true);

            context.putClassConfig(q.getClass(), cfg);
        }

        // Second pass: create direct Card cards.
        for (Viewable q : viewables) {
            if (q == null) {
                continue;
            }

            ViewConfig cfg =
                    context.configFor(q.getClass());

            if (cfg == null) {
                cfg = ViewConfig.all(q.getClass())
                                .setAddListener(true)
                                .setThumb(true);
            }

            Card panel =
                    new Card(q, cfg, context, false);

            context.registerTopLevel(q, panel);

            tuneCardSize(panel);

            cards.add(panel);

            String name = q.getName();

            if (name != null && !name.isEmpty()) {
                cardsByName.putIfAbsent(name, panel);
            }
        }

        // raw image entries unchanged
    }

    private void tuneCardSize(JPanel panel) {
        if (!containsImagePane(panel)) {
            return;
        }

        // A live card can be expanded in place (reference chips), so it must
        // keep growing past its initial size. Enforce the minimum footprint as
        // a floor that still lets the natural preferred size win, rather than
        // freezing it with setPreferredSize.
        if (panel instanceof Card card) {
            card.setCardSizeFloor(new Dimension(260, 260));
            return;
        }

        // Static holders (raw image entries) never change, so a frozen size
        // is fine.
        Dimension pref = panel.getPreferredSize();
        panel.setPreferredSize(new Dimension(
                Math.max(pref.width, 260), Math.max(pref.height, 260)));
        panel.setMinimumSize(new Dimension(220, 220));
    }

    private boolean containsImagePane(Component c) {
        if (c instanceof ImagePane) {
            return true;
        }

        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsImagePane(child)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void createFrame(String title, int numColumns) {
        if (virtualList == null) {
            createCardsPanel(numColumns);
        }
        frame = new JFrame(
                viewables.size() == 1
                        ? title
                        : (title + ", " + viewables.size()));

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(6, 6));

        if (!viewables.isEmpty()) {
            CardSearchBarFactory factory = CardSearchBarFactory.active();
            JComponent searchBar = factory == null
                    ? null
                    : factory.createSearchBar(this, viewables.get(0).getClass());
            if (searchBar != null) {
                frame.add(searchBar, BorderLayout.NORTH);
            }
        }

        frame.add(cardsScrollPane, BorderLayout.CENTER);

        frame.setSize(1200, 700);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
    }

    public void show() {
        if (frame != null) {
            frame.setVisible(true);
            frame.toFront();
        }
    }

    public void show(String title) {
        show(title, 1);
    }

    public void show(String title, int numColumns) {
        if (frame == null) {
            createFrame(title, numColumns);
        }

        show();
    }

    private record RawImageEntry(String title,
                                 ImagePane imagePane) {
    }
}