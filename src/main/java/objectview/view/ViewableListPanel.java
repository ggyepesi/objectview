package objectview.view;

import objectview.Viewable;
import objectview.render.RenderingMode;
import objectview.search.SearchPanel;
import objectview.viewconfig.ViewConfig;

import javax.swing.JPanel;
import javax.swing.JComponent;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * A replaceable, searchable list of {@link Viewable} instances.
 *
 * <p>{@link SearchableView} deliberately represents one stable result set. Browsers,
 * inspectors and master/detail screens instead keep the surface while replacing its
 * members as the current object changes. This small owner performs that lifecycle:
 * every non-empty result is still rendered by SearchableView and therefore by a
 * virtualized card list, while the previous view detaches its render-context hooks
 * before it is discarded.
 *
 * <p>The component knows nothing about navigation or loading. A host supplies items,
 * observes ordinary objectview selection, and decides whether selection, a button or
 * another gesture activates an item.
 */
public final class ViewableListPanel extends JPanel implements AutoCloseable {
    private final Class<? extends Viewable> type;
    private final String emptyMessage;
    private Consumer<Viewable> selectionListener = ignored -> { };
    private Consumer<List<Viewable>> selectionSetListener;
    private Consumer<Viewable> activationListener;
    private java.util.function.Function<Object, String> valueLinker;
    private java.util.Set<String> hiddenFields = java.util.Set.of();
    private boolean controlsExpanded = true;
    private RenderingMode mode = RenderingMode.CARD;
    private SearchPanel.ConfigState configState;
    private String searchText = "";
    private boolean sorted;
    private boolean searchFocused;
    private int searchCaretPosition;
    private Viewable configSample;
    private SearchableView view;
    private Viewable selected;
    private List<Viewable> currentItems = List.of();
    private final JPanel externalControlsHost = new JPanel(new BorderLayout());
    private boolean externalControls;

    public ViewableListPanel(Class<? extends Viewable> type, String emptyMessage) {
        super(new BorderLayout());
        // This component is commonly placed in a split pane or tab. Its scroll pane,
        // not the natural height of every card plus the controls, owns overflow.
        setMinimumSize(new java.awt.Dimension(0, 0));
        if (type == null) throw new IllegalArgumentException("Viewable type is required");
        this.type = type;
        this.emptyMessage = emptyMessage == null ? "No instances." : emptyMessage;
        // An auxiliary/browser list starts compact: minor metadata remains available
        // by name in View Config, but the user opts it into the cards explicitly.
        this.configState = new SearchPanel.ConfigState(
                null, null, ViewConfig.all(type).setAddListener(true).setThumb(true));
        setViewables(List.of());
    }

    public void onSelectionChanged(Consumer<Viewable> listener) {
        selectionListener = listener == null ? ignored -> { } : listener;
    }

    public void onSelectionSetChanged(Consumer<List<Viewable>> listener) {
        selectionSetListener = listener;
    }

    public void onActivated(Consumer<Viewable> listener) {
        activationListener = listener;
        // Rebuild so the old registration is detached and the new one is owned by
        // the replacement view's normal lifecycle.
        if (view != null) setViewables(currentItems);
    }

    public void valueLinker(java.util.function.Function<Object, String> linker) {
        valueLinker = linker;
        if (view != null) view.renderContext().setValueLinker(linker);
    }

    public void hiddenFields(java.util.Set<String> fields) {
        hiddenFields = fields == null ? java.util.Set.of() : java.util.Set.copyOf(fields);
    }

    public boolean controlsExpanded() { return controlsExpanded; }

    /** A stable host for this panel's Search/Sort/View controls. Calling this opts
     * the panel out of inline controls; subsequent result replacement swaps only
     * the host's contents, so a surrounding tab never has to be rebuilt. */
    public JComponent externalControls() {
        if (!externalControls) {
            externalControls = true;
            externalControlsHost.setMinimumSize(new java.awt.Dimension(0, 0));
            setViewables(currentItems);
        }
        return externalControlsHost;
    }

    public void setControlsExpanded(boolean expanded) {
        controlsExpanded = expanded;
        if (view != null) view.setControlsExpanded(expanded);
    }

    public Viewable selected() { return selected; }

    public void setViewables(Collection<? extends Viewable> members) {
        List<Viewable> items = members == null ? List.of() : List.copyOf(members);
        disposeView();
        currentItems = items;
        if (!items.isEmpty()) configSample = items.get(0);
        selected = null;
        selectionListener.accept(null);
        if (selectionSetListener != null) selectionSetListener.accept(List.of());
        SearchableView.Builder builder = SearchableView.builder(items)
                .type(type)
                .sample(configSample)
                .mode(mode)
                .configState(configState)
                .configListener(state -> configState = state)
                .inlineControls(!externalControls)
                .collapsible(false)
                .emptyMessage(emptyMessage)
                .hiddenFields(hiddenFields)
                .controlsExpanded(controlsExpanded)
                .valueLinker(valueLinker)
                .activationListener(activationListener)
                .selectionListener(value -> {
                    selected = value instanceof Viewable item ? item : null;
                    selectionListener.accept(selected);
                });
        if (selectionSetListener != null) {
            builder.selectionSetListener(values -> selectionSetListener.accept(values.stream()
                    .filter(Viewable.class::isInstance).map(Viewable.class::cast).toList()));
        }
        view = builder.build();
        view.setMinimumSize(new java.awt.Dimension(0, 0));
        updateExternalControls();
        add(view, BorderLayout.CENTER);
        revalidate();
        repaint();
        view.restoreInteraction(searchText, sorted);
        SearchableView installedView = view;
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (this.view == installedView) {
                installedView.refreshViewport();
                installedView.restoreSearchFocus(searchFocused, searchCaretPosition);
            }
        });
    }

    @Override public void close() {
        disposeView();
        selected = null;
        externalControlsHost.removeAll();
        removeAll();
    }

    // Package-private test seam: selection itself belongs to RenderContext; this
    // verifies replacement without simulating Swing mouse coordinates.
    objectview.render.RenderContext renderContextForTest() {
        return view == null ? null : view.renderContext();
    }

    private void disposeView() {
        if (view == null) return;
        // A browser replaces its result set but not the reader's UI choice.
        controlsExpanded = view.controlsExpanded();
        mode = view.mode();
        configState = view.configState();
        searchText = view.searchText();
        sorted = view.sorted();
        searchFocused = view.searchFieldFocused();
        searchCaretPosition = view.searchCaretPosition();
        view.dispose();
        remove(view);
        view = null;
    }

    private void updateExternalControls() {
        if (!externalControls) return;
        externalControlsHost.removeAll();
        JComponent controls = view == null ? null : view.controlsComponent();
        if (controls != null) {
            externalControlsHost.add(controls, BorderLayout.CENTER);
        } else {
            externalControlsHost.add(new javax.swing.JLabel("   " + emptyMessage),
                    BorderLayout.NORTH);
        }
        externalControlsHost.revalidate();
        externalControlsHost.repaint();
    }
}
