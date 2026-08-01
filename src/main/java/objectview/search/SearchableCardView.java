package objectview.search;

import objectview.Viewable;
import objectview.field.FieldSchema;
import objectview.render.CardListView;
import objectview.render.RenderContext;
import objectview.viewconfig.FieldTypeSource;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The shared searchable instance browser: one {@link CardListView}, one
 * {@link SearchPanel}, and one consistently wired {@link RenderContext}.
 */
public final class SearchableCardView extends JPanel {
    private final CardListView cards;
    private final SearchPanel search;
    private final RenderContext context;

    private SearchableCardView(Builder builder) {
        super(new BorderLayout(4, 4));

        context = builder.context == null ? new RenderContext() : builder.context;
        if (builder.collapsible != null) {
            context.setCollapsibleCards(builder.collapsible);
        }
        if (builder.fieldSchemas != null) {
            context.setFieldSchemaResolver(builder.fieldSchemas);
        }
        if (builder.selectionListener != null) {
            context.setSelectionEnabled(true);
            context.addSelectionListener(builder.selectionListener);
        }

        cards = new CardListView();
        cards.setRenderContext(context);
        builder.members.forEach(cards::addViewable);
        cards.createCardsPanel(builder.columns);

        Class<? extends Viewable> type = builder.type;
        if (type == null && builder.sample != null) {
            type = viewableClass(builder.sample);
        }
        if (type == null && !builder.members.isEmpty()) {
            type = viewableClass(builder.members.get(0));
        }

        if (type != null && !builder.members.isEmpty()) {
            search = builder.sample == null
                    ? new SearchPanel(type, null, builder.configState,
                            builder.subtypeConfigs)
                    : new SearchPanel(type, builder.sample, builder.configState,
                            builder.subtypeConfigs);
            search.setHiddenFields(builder.hiddenFields);
            search.setFieldTypes(builder.fieldTypes);
            search.setRenderContext(context);
            search.setConfigListener(builder.configListener);
            search.setTargetAndApplyViewConfig(
                    cards.getCardsPanel(), cards.getCardsScrollPane());
            search.setCoordinated(builder.coordinated);
            cards.addTargetListener(search);
            add(search, BorderLayout.NORTH);
        } else {
            search = null;
            if (builder.emptyMessage != null && !builder.emptyMessage.isBlank()) {
                add(new JLabel("   " + builder.emptyMessage), BorderLayout.NORTH);
            }
        }
        add(cards.getCardsScrollPane(), BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            if (cards.getVirtualList() != null) cards.getVirtualList().rebuild();
        });
    }

    public static Builder builder(Collection<? extends Viewable> members) {
        return new Builder(members);
    }

    public CardListView cards() { return cards; }
    public SearchPanel search() { return search; }
    public RenderContext renderContext() { return context; }

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
        private Function<Viewable, FieldSchema> fieldSchemas;
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

        public Builder type(Class<? extends Viewable> value) { type = value; return this; }
        public Builder sample(Viewable value) { sample = value; return this; }
        public Builder hiddenFields(Set<String> value) {
            hiddenFields = value == null ? Set.of() : value; return this;
        }
        public Builder fieldTypes(FieldTypeSource value) { fieldTypes = value; return this; }
        public Builder fieldSchemas(Function<Viewable, FieldSchema> value) {
            fieldSchemas = value; return this;
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

        public SearchableCardView build() { return new SearchableCardView(this); }
    }
}
