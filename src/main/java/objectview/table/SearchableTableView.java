package objectview.table;

import objectview.Viewable;
import objectview.field.ViewableFieldPaths;
import objectview.search.SearchPanel;
import objectview.viewconfig.FieldTypeSource;
import objectview.viewconfig.ViewConfig;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Searchable ObjectView table assembled from the same SearchPanel, ViewConfig and
 * schema projection used by SearchableCardView. Only the spatial presentation is
 * different: one Viewable per row and one configured field path per column.
 */
public final class SearchableTableView extends JPanel {
    private final ViewableTable table;
    private final JScrollPane scroll;
    private final SearchPanel search;

    private SearchableTableView(Builder builder) {
        super(new BorderLayout(4, 4));

        Class<? extends Viewable> type = builder.type;
        if (type == null && builder.sample != null) type = viewableClass(builder.sample);
        if (type == null && !builder.members.isEmpty()) {
            type = viewableClass(builder.members.get(0));
        }

        Viewable columnSample = builder.sample != null
                ? builder.sample
                : builder.members.isEmpty() ? null : builder.members.get(0);
        table = new ViewableTable(builder.members,
                (row, config) -> configuredPaths(row, config,
                        columnSample, builder.fieldTypes, builder.subtypeConfigs),
                builder.selectionListener);
        scroll = new JScrollPane(table);
        scroll.getVerticalScrollBar().setUnitIncrement(table.getRowHeight());

        if (type != null && !builder.members.isEmpty()) {
            search = builder.sample == null
                    ? new SearchPanel(type, null, builder.configState,
                            builder.subtypeConfigs, builder.fieldTypes)
                    : new SearchPanel(type, builder.sample, builder.configState,
                            builder.subtypeConfigs, builder.fieldTypes);
            search.setHiddenFields(builder.hiddenFields);
            search.setConfigListener(builder.configListener);
            search.setTargetAndApplyViewConfig(table, scroll);
            search.setCoordinated(builder.coordinated);
            add(search, BorderLayout.NORTH);
        } else {
            search = null;
            if (builder.emptyMessage != null && !builder.emptyMessage.isBlank()) {
                add(new JLabel("   " + builder.emptyMessage), BorderLayout.NORTH);
            }
        }
        add(scroll, BorderLayout.CENTER);
    }

    public static Builder builder(Collection<? extends Viewable> members) {
        return new Builder(members);
    }

    public ViewableTable table() { return table; }
    public JScrollPane scrollPane() { return scroll; }
    public SearchPanel search() { return search; }

    private static List<ViewableFieldPaths.FieldPath> configuredPaths(
            Viewable row, ViewConfig config, Viewable stableSample,
            FieldTypeSource root,
            List<SearchPanel.SubtypeConfig> subtypes) {
        Map<String, ViewableFieldPaths.FieldPath> paths = new LinkedHashMap<>();
        if (root != null) {
            add(paths, ViewableFieldPaths.collectFromSchema(config, root, false));
            for (SearchPanel.SubtypeConfig subtype : subtypes) {
                if (subtype == null || subtype.fieldTypes() == null) continue;
                if (subtype.applies() == null || subtype.applies().test(row)) {
                    add(paths, ViewableFieldPaths.collectFromSchema(
                            config, subtype.fieldTypes(), false));
                }
            }
        } else if (stableSample != null) {
            add(paths, ViewableFieldPaths.collectFromSample(
                    stableSample, config, ViewableFieldPaths.ALL_FIELDS));
        } else {
            add(paths, ViewableFieldPaths.collect(config, ViewableFieldPaths.ALL_FIELDS));
        }
        return List.copyOf(paths.values());
    }

    private static void add(Map<String, ViewableFieldPaths.FieldPath> target,
                            List<ViewableFieldPaths.FieldPath> paths) {
        for (ViewableFieldPaths.FieldPath path : paths) {
            target.putIfAbsent(path.dotted(), path);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Viewable> viewableClass(Viewable value) {
        return (Class<? extends Viewable>) value.getClass();
    }

    public static final class Builder {
        private final List<Viewable> members = new ArrayList<>();
        private Class<? extends Viewable> type;
        private Viewable sample;
        private Set<String> hiddenFields = Set.of();
        private FieldTypeSource fieldTypes;
        private Consumer<Object> selectionListener;
        private boolean coordinated;
        private String emptyMessage = "No instances.";
        private SearchPanel.ConfigState configState;
        private Consumer<SearchPanel.ConfigState> configListener;
        private List<SearchPanel.SubtypeConfig> subtypeConfigs = List.of();

        private Builder(Collection<? extends Viewable> members) {
            if (members != null) this.members.addAll(members);
        }

        public Builder type(Class<? extends Viewable> value) { type = value; return this; }
        public Builder sample(Viewable value) { sample = value; return this; }
        public Builder hiddenFields(Set<String> value) {
            hiddenFields = value == null ? Set.of() : value; return this;
        }
        public Builder fieldTypes(FieldTypeSource value) { fieldTypes = value; return this; }
        public Builder selectionListener(Consumer<Object> value) {
            selectionListener = value; return this;
        }
        public Builder coordinated(boolean value) { coordinated = value; return this; }
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

        public SearchableTableView build() { return new SearchableTableView(this); }
    }
}
