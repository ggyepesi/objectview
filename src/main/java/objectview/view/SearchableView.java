package objectview.view;

import objectview.Viewable;
import objectview.field.FieldSchema;
import objectview.field.ViewableFieldPaths;
import objectview.render.CardListView;
import objectview.render.RenderContext;
import objectview.render.RenderingMode;
import objectview.search.SearchPanel;
import objectview.table.ViewableColumnsView;
import objectview.viewconfig.FieldTypeSource;
import objectview.viewconfig.ViewConfig;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One searchable instance browser that can render its members as CARDS or a TABLE, chosen
 * at runtime via a mode combo in the view-config dialog. It builds ONE {@link SearchPanel}
 * (search/sort/view config) and swaps only the {@code VirtualizedContainer} underneath —
 * so the mode is a pure presentation choice; items, config and search/sort state survive
 * the swap. Unifies the former SearchableCardView / SearchableTableView assemblies.
 */
public final class SearchableView extends JPanel {

    private final Builder builder;
    private final RenderContext context;
    private final SearchPanel search;
    private RenderingMode mode;
    private JComponent center;
    private CardListView cardList;        // active when mode == CARD
    private ViewableColumnsView table;    // active when mode == TABLE

    private SearchableView(Builder b) {
        super(new BorderLayout(4, 4));
        this.builder = b;
        this.mode = b.mode == null ? RenderingMode.CARD : b.mode;
        this.context = b.context == null ? new RenderContext() : b.context;
        if (b.collapsible != null) context.setCollapsibleCards(b.collapsible);
        if (b.fieldSchemas != null) context.setFieldSchemaResolver(b.fieldSchemas);
        if (b.cardDecorator != null) context.setCardDecorator(b.cardDecorator);
        if (b.selectionListener != null) {
            context.setSelectionEnabled(true);
            context.addSelectionListener(b.selectionListener);
        }

        Class<? extends Viewable> type = b.type;
        if (type == null && b.sample != null) type = viewableClass(b.sample);
        if (type == null && !b.members.isEmpty()) type = viewableClass(b.members.get(0));

        if (type == null || b.members.isEmpty()) {
            search = null;
            if (b.emptyMessage != null && !b.emptyMessage.isBlank()) {
                add(new JLabel("   " + b.emptyMessage), BorderLayout.NORTH);
            }
            return;
        }

        search = new SearchPanel(type, b.sample, b.configState, b.subtypeConfigs, b.fieldTypes);
        search.setHiddenFields(b.hiddenFields);
        search.setConfigListener(b.configListener);
        search.setCoordinated(b.coordinated);

        JComboBox<RenderingMode> modeCombo = new JComboBox<>(RenderingMode.values());
        modeCombo.setSelectedItem(mode);
        modeCombo.addActionListener(e -> switchMode((RenderingMode) modeCombo.getSelectedItem()));
        search.setRenderingModeControl(modeCombo);

        add(search, BorderLayout.NORTH);
        installContainer();
    }

    public static Builder builder(Collection<? extends Viewable> members) {
        return new Builder(members);
    }

    public SearchPanel search() { return search; }
    public RenderContext renderContext() { return context; }
    public RenderingMode mode() { return mode; }

    private void switchMode(RenderingMode next) {
        if (next == null || next == mode || search == null) return;
        mode = next;
        installContainer();
        revalidate();
        repaint();
    }

    /** (Re)build the container for the current mode, rebind the shared SearchPanel to it,
     *  and replace the centre. The SearchPanel — and thus all search/sort/view state —
     *  persists across the swap. */
    private void installContainer() {
        if (center != null) {
            remove(center);
            center = null;
        }
        center = mode == RenderingMode.TABLE ? buildTable() : buildCards();
        add(center, BorderLayout.CENTER);
    }

    private JComponent buildCards() {
        cardList = new CardListView();
        table = null;
        cardList.setRenderContext(context);
        builder.members.forEach(cardList::addViewable);
        cardList.createCardsPanel(builder.columns);
        search.setRenderContext(context);
        search.setTargetAndApplyViewConfig(
                cardList.getCardsPanel(), cardList.getCardsScrollPane());
        cardList.addTargetListener(search);
        CardListView cards = cardList;
        SwingUtilities.invokeLater(() -> {
            if (cards.getVirtualList() != null) cards.getVirtualList().rebuild();
        });
        return cardList.getCardsScrollPane();
    }

    private JComponent buildTable() {
        Viewable columnSample = builder.sample != null ? builder.sample
                : builder.members.isEmpty() ? null : builder.members.get(0);
        // The table is a LAYOUT of the card path: rows reuse ValueRenderer's components
        // (media/chip/copyable-text) through the shared RenderContext, so selection, copy and
        // images come for free — only the column arrangement differs from CARD mode.
        // Columns come from the DECLARED shape — the base schema plus every declared
        // subtype — never from sampling the members. That is what keeps the header
        // O(declared subtypes) instead of O(items).
        table = new ViewableColumnsView(builder.members, context,
                () -> tablePaths(search.getViewConfig(), columnSample,
                        builder.type, builder.fieldTypes, builder.subtypeConfigs));
        cardList = null;
        search.setTargetAndApplyViewConfig(
                table, table.scrollPane(), table.scrollPane());
        return table.scrollPane();
    }

    /** The active card container, or null when the current mode is TABLE. */
    public CardListView cardList() { return cardList; }

    /** The active table (a card-path columns layout), or null when the current mode is CARD. */
    public ViewableColumnsView table() { return table; }

    /**
     * The table's column paths, projected from the DECLARED shape: the base schema
     * (or the stable sample's class when there is none) plus every declared subtype
     * that contributes its own fields. It never inspects the members — a subtype is
     * a declaration, not something to discover by scanning, so the projection costs
     * O(declared subtypes) whether the view holds ten rows or five hundred thousand.
     *
     * <p>Consequence, accepted deliberately: a declared subtype with no instances
     * present still contributes its column, empty. The declared class is the shape.
     */
    private static List<ViewableFieldPaths.PathInfo> tablePaths(
            ViewConfig config, Viewable stableSample,
            Class<? extends Viewable> declaredType,
            FieldTypeSource root, List<SearchPanel.SubtypeConfig> subtypes) {
        ViewConfig effective = config == null ? new ViewConfig() : config;
        Map<String, ViewableFieldPaths.PathInfo> paths = new LinkedHashMap<>();
        if (root != null) {
            addPaths(paths, ViewableFieldPaths.collectFromSchema(effective, root, false));
            for (SearchPanel.SubtypeConfig subtype : subtypes) {
                if (subtype == null || subtype.fieldTypes() == null) continue;
                addPaths(paths, ViewableFieldPaths.collectFromSchema(
                        effective, subtype.fieldTypes(), false));
            }
        } else if (stableSample != null) {
            addPaths(paths, ViewableFieldPaths.collectFromSample(
                    stableSample, effective, ViewableFieldPaths.ALL_FIELDS));
        } else {
            addPaths(paths, ViewableFieldPaths.collect(effective, ViewableFieldPaths.ALL_FIELDS));
        }
        return displayFirst(List.copyOf(paths.values()), declaredType, stableSample);
    }

    /** The display field reads as the row's title, so it leads the columns. Resolved
     *  from the declared class — the same source the columns themselves come from. */
    private static List<ViewableFieldPaths.PathInfo> displayFirst(
            List<ViewableFieldPaths.PathInfo> ordered,
            Class<? extends Viewable> declaredType, Viewable stableSample) {
        Class<? extends Viewable> type = declaredType != null ? declaredType
                : stableSample == null ? null : viewableClass(stableSample);
        if (type == null) return ordered;
        String displayKey = objectview.field.ViewableContractFieldSet.displayKey(type);
        List<ViewableFieldPaths.PathInfo> out = new ArrayList<>(ordered);
        for (int i = 1; i < out.size(); i++) {
            if (out.get(i).dotted().equals(displayKey)) {
                out.add(0, out.remove(i));
                break;
            }
        }
        return List.copyOf(out);
    }

    private static void addPaths(Map<String, ViewableFieldPaths.PathInfo> target,
                                 List<ViewableFieldPaths.PathInfo> paths) {
        for (ViewableFieldPaths.PathInfo path : paths) {
            target.putIfAbsent(path.dotted(), path);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Viewable> viewableClass(Viewable value) {
        return (Class<? extends Viewable>) value.getClass();
    }

    public static final class Builder {
        private final List<Viewable> members = new ArrayList<>();
        private RenderingMode mode = RenderingMode.CARD;
        private Class<? extends Viewable> type;
        private Viewable sample;
        private Set<String> hiddenFields = Set.of();
        private FieldTypeSource fieldTypes;
        private Function<Viewable, FieldSchema> fieldSchemas;
        private Function<Viewable, JComponent> cardDecorator;
        private Consumer<Object> selectionListener;
        private RenderContext context;
        private Boolean collapsible;
        private boolean coordinated;
        private int columns = 1;
        private String emptyMessage = "No instances.";
        private SearchPanel.ConfigState configState;
        private Consumer<SearchPanel.ConfigState> configListener;
        private List<SearchPanel.SubtypeConfig> subtypeConfigs = List.of();

        private Builder(Collection<? extends Viewable> members) {
            if (members != null) this.members.addAll(members);
        }

        public Builder mode(RenderingMode value) {
            mode = value == null ? RenderingMode.CARD : value; return this;
        }
        public Builder type(Class<? extends Viewable> value) { type = value; return this; }
        public Builder sample(Viewable value) { sample = value; return this; }
        public Builder hiddenFields(Set<String> value) {
            hiddenFields = value == null ? Set.of() : value; return this;
        }
        public Builder fieldTypes(FieldTypeSource value) { fieldTypes = value; return this; }
        public Builder fieldSchemas(Function<Viewable, FieldSchema> value) {
            fieldSchemas = value; return this;
        }
        public Builder cardDecorator(Function<Viewable, JComponent> value) {
            cardDecorator = value; return this;
        }
        public Builder selectionListener(Consumer<Object> value) {
            selectionListener = value; return this;
        }
        public Builder renderContext(RenderContext value) { context = value; return this; }
        public Builder collapsible(boolean value) { collapsible = value; return this; }
        public Builder coordinated(boolean value) { coordinated = value; return this; }
        public Builder columns(int value) { columns = Math.max(1, value); return this; }
        public Builder emptyMessage(String value) { emptyMessage = value; return this; }
        public Builder configState(SearchPanel.ConfigState value) {
            configState = value; return this;
        }
        public Builder configListener(Consumer<SearchPanel.ConfigState> value) {
            configListener = value; return this;
        }
        public Builder subtypeConfigs(List<SearchPanel.SubtypeConfig> value) {
            subtypeConfigs = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public SearchableView build() { return new SearchableView(this); }
    }
}
