package objectview.search;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.field.DynamicFields;
import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSchema;
import objectview.viewconfig.FieldTypeSource;
import objectview.render.Card;
import objectview.render.RenderingMode;
import objectview.view.SearchableView;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchableCardViewTest {

    @Test void wiresCardsSearchAndContextOnce() {
        EdtTests.onEdt(() -> {
            Item item = new Item("one");
            SearchableView view = SearchableView.builder(List.of(item))
                    .sample(item)
                    .hiddenFields(Set.of("internal"))
                    .collapsible(true)
                    .build();

            assertNotNull(view.cardList().getVirtualList());
            assertNotNull(view.search());
            assertTrue(view.renderContext().collapsibleCards());
            assertTrue(view.search().getViewConfig().isAllMinorFields(),
                    "an expanded instance starts with its complete field set");
            });
    }

    @Test void initialViewConfigControlsVirtualizedCards() {
        EdtTests.onEdt(() -> {
            Item item = new Item("one", "secret");
            ViewConfig viewConfig = ViewConfig.of(Item.class);
            viewConfig.setAllFields(false);
            viewConfig.addField(
                    objectview.field.ViewableContractFieldSet.DISPLAY_KEY, ViewConfig.leaf());
            AtomicReference<SearchPanel.ConfigState> retained = new AtomicReference<>();

            SearchableView view = SearchableView.builder(List.of(item))
                    .sample(item)
                    .configState(new SearchPanel.ConfigState(null, null, viewConfig))
                    .configListener(retained::set)
                    .build();
            view.search().applyView();

            SearchableView replacement = SearchableView.builder(List.of(item))
                    .sample(item)
                    .configState(retained.get())
                    .build();

            Card card = (Card) replacement.cardList().getVirtualList().buildIfNeeded(item);
            assertTrue(componentText(card).contains("one"));
            assertFalse(componentText(card).contains("secret"));
            });
    }

    @Test void subtypeAdditionalFieldsShareOneHierarchyConfig() {
        EdtTests.onEdt(() -> {
            Item base = new Item("base", "base-secret");
            SubItem subtype = new SubItem("sub", "sub-secret");
            ViewConfig baseView = ViewConfig.of(Item.class);
            baseView.setAllFields(false);
            baseView.addField(
                    objectview.field.ViewableContractFieldSet.DISPLAY_KEY, ViewConfig.leaf());
            ViewConfig disabledSubtype = ViewConfig.of(SubItem.class);
            disabledSubtype.setAllFields(false);
            SearchPanel.ConfigState state = new SearchPanel.ConfigState(
                    null, null, baseView, java.util.Map.of(), java.util.Map.of(),
                    java.util.Map.of("SubItem", disabledSubtype));

            SearchableView view = SearchableView.builder(List.of(base, subtype))
                    .sample(base)
                    .configState(state)
                    .subtypeConfigs(List.of(new SearchPanel.SubtypeConfig(
                            "SubItem", "Item", subtype, null, Set.of("internal"),
                            value -> value instanceof SubItem)))
                    .build();

            Card card = (Card) view.cardList().getVirtualList().buildIfNeeded(subtype);
            assertTrue(componentText(card).contains("sub"));
            assertFalse(componentText(card).contains("sub-secret"));
            assertTrue(view.search().configState().subtypeView().containsKey("SubItem"));
            });
    }

    @Test void schemaIsInstalledBeforeNullSampleDynamicConfigIsMaterialized() {
        EdtTests.onEdt(() -> {
            DynamicItem item = new DynamicItem("visible-value");
            FieldTypeSource schema = new FieldTypeSource() {
                @Override public FieldTypeInfo field(String name) {
                    return "detail".equals(name)
                            ? new FieldTypeInfo("String", false, false, null, null,
                                    null, objectview.field.FieldRole.NONE,
                                    objectview.field.FieldKind.TEXT,
                                    objectview.field.FieldKind.TEXT)
                            : null;
                }

                @Override public List<String> fieldNames() { return List.of("detail"); }
            };

            // Snapshot-backed views intentionally have no synthetic sample; fields come
            // from their saved schema. They must still survive the initial config fold.
            SearchableView view = SearchableView.builder(List.of(item))
                    .fieldTypes(schema)
                    .collapsible(true)
                    .build();

            assertTrue(view.search().getViewConfig().hasField("detail"),
                    view.search().getViewConfig().getFields().keySet().toString());
            Card collapsed = (Card) view.cardList().getVirtualList().buildIfNeeded(item);
            JLabel toggle = findLabel(collapsed, "▶ ");
            assertNotNull(toggle);
            java.awt.event.MouseEvent click = new java.awt.event.MouseEvent(
                    toggle, java.awt.event.MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 2, 2, 1, false,
                    java.awt.event.MouseEvent.BUTTON1);
            for (java.awt.event.MouseListener listener : toggle.getMouseListeners()) {
                listener.mousePressed(click);
            }
            Card card = (Card) view.cardList().getVirtualList().buildIfNeeded(item);
            assertTrue(card.getComponentCount() > 1,
                    "a saved dynamic field must not vanish before its schema is installed");
            });
    }

    @Test void cardsAndTablesSearchThroughTheSameDeclaredSchemaTheyRender() {
        EdtTests.onEdt(() -> {
            SchemaOnlyItem item = new SchemaOnlyItem("Schema-only title");
            FieldSchema schema = () -> List.of(FieldRef.computed(
                    "caption", "Caption", FieldKind.TEXT, FieldRole.DISPLAY));
            FieldTypeSource fieldTypes = new FieldTypeSource() {
                @Override public FieldTypeInfo field(String name) {
                    return "caption".equals(name)
                            ? new FieldTypeInfo("String", false, false,
                                    null, null, "Caption", FieldRole.DISPLAY,
                                    FieldKind.TEXT, FieldKind.TEXT)
                            : null;
                }
                @Override public List<String> fieldNames() { return List.of("caption"); }
            };
            ViewConfig config = ViewConfig.of(SchemaOnlyItem.class);
            config.setAllFields(false);
            config.addField("caption", ViewConfig.leaf());

            for (RenderingMode mode : RenderingMode.values()) {
                SearchableView view = SearchableView.builder(List.of(item))
                        .type(SchemaOnlyItem.class)
                        .mode(mode)
                        .fieldTypes(fieldTypes)
                        .fieldSchemas(ignored -> schema)
                        .configState(new SearchPanel.ConfigState(config, null, config))
                        .build();

                view.search().runCoordinatedSearch("schema-only title");
                assertEquals(List.of(item), view.search().currentHits(),
                        mode + " search bypassed the rendering schema");
                view.dispose();
            }
        });
    }

    private static String componentText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel label) text.append(label.getText()).append('\n');
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                text.append(componentText(child));
            }
        }
        return text.toString();
    }

    private static JLabel findLabel(Component component, String text) {
        if (component instanceof JLabel label && text.equals(label.getText())) return label;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel found = findLabel(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static class Item extends ViewableAdapter {
        private final String name;
        private final String internal;
        private Item(String name) { this(name, ""); }
        private Item(String name, String internal) {
            this.name = name;
            this.internal = internal;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class SubItem extends Item {
        private SubItem(String name, String internal) { super(name, internal); }
    }

    private static final class DynamicItem extends ViewableAdapter
            implements DynamicFields {
        private final java.util.Map<String, Object> values =
                new java.util.LinkedHashMap<>();

        private DynamicItem(String detail) { values.put("detail", detail); }
        @Override public String getIdentifier() { return "dynamic"; }
        @Override public String getDisplayName() { return "Dynamic"; }
        @Override public java.util.Map<String, Object> dynamicFieldValues() {
            return values;
        }
    }

    private static final class SchemaOnlyItem extends ViewableAdapter
            implements DynamicFields {
        private final String display;
        private final java.util.Map<String, Object> values =
                new java.util.LinkedHashMap<>();

        private SchemaOnlyItem(String display) { this.display = display; }
        @Override public String getIdentifier() { return display; }
        @Override public String getDisplayName() { return display; }
        @Override public java.util.Map<String, Object> dynamicFieldValues() {
            return values;
        }
    }
}
