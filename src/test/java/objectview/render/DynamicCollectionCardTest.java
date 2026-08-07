package objectview.render;

import objectview.ViewableAdapter;
import objectview.field.DynamicFields;
import objectview.field.FieldKind;
import objectview.field.FieldPath;
import objectview.field.FieldProperties;
import objectview.field.FieldRef;
import objectview.field.FieldSchema;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicCollectionCardTest {

    @Test
    void dynamicCollectionsUseTheSameCollapsibleHeaderAsDeclaredCollections()
            throws Exception {
        DynamicThing thing = new DynamicThing();
        thing.values.put("images", List.of("one", "two"));

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(thing, ViewConfig.all(DynamicThing.class), false));

        assertNotNull(find(card[0], CollectionHeader.class),
                "snapshot-backed collections must retain an expand/collapse chip");
    }

    @Test
    void dynamicReferencesStartCollapsedLikeDeclaredReferences()
            throws Exception {
        DynamicThing parent = new DynamicThing("parent");
        DynamicThing child = new DynamicThing("child");
        child.values.put("detail", "hidden until expanded");
        parent.values.put("child", child);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(
                        parent, ViewConfig.all(DynamicThing.class), false));

        assertNotNull(find(card[0], ReferenceRow.class));
        assertEquals(1, count(card[0], Card.class),
                "a dynamic reference must not render its nested card eagerly");
    }

    @Test
    void referenceUsesItsFieldConfigWhenLogicalTypesShareOneAdapterClass()
            throws Exception {
        DynamicThing language = new DynamicThing("French");
        language.values.put("nativeName", "français");
        language.schema = () -> List.of(FieldRef.described(
                "nativeName", FieldKind.TEXT, FieldKind.TEXT, "String",
                false, false, null, false, false,
                false, false, "", false));

        DynamicThing state = new DynamicThing("France");
        List<DynamicThing> languages = List.of(language);
        state.values.put("languages", languages);
        state.schema = () -> List.of(FieldRef.described(
                "languages", FieldKind.COLLECTION, FieldKind.REFERENCE,
                "List<Language>", true, true, "Language", false, false,
                false, false, "", true));

        ViewConfig languageConfig = ViewConfig.leaf();
        languageConfig.addField("nativeName", ViewConfig.leaf());
        ViewConfig stateConfig = ViewConfig.leaf();
        stateConfig.addField("languages", languageConfig);

        RenderContext context = new RenderContext();
        // This is the important snapshot condition: State and Language have
        // different logical schemas but the same runtime adapter class.
        context.putClassConfig(DynamicThing.class, stateConfig);
        context.setCollectionExpanded(languages, true);
        context.setExpanded(language, true);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(state, stateConfig, context, false));

        assertNotNull(find(card[0], TextBlock.class),
                "the language field config must not be replaced by the "
                        + "state config merely because both use one adapter class");
    }

    @Test
    void referenceRowsUseTheTargetsQualifiedReferenceLabel() {
        DynamicThing target = new DynamicThing("Vienna") {
            @Override public String getReferenceLabel() {
                return "All/Capitals/VI/Vienna";
            }
        };

        ReferenceRow row = new ReferenceRow(
                "groups", FieldPath.of("groups"), target,
                new RenderContext(), ViewConfig.all(DynamicThing.class),
                "Vienna", false);

        assertEquals("All/Capitals/VI/Vienna",
                row.getClientProperty(
                        FieldProperties.FIELD_VALUE_PROPERTY));
    }

    @Test
    void aReferenceChipCarriesTheCardIdentityDecorationWhenNonNull() throws Exception {
        DynamicThing parent = new DynamicThing("parent");
        DynamicThing decorated = new DynamicThing("decorated");
        DynamicThing plain = new DynamicThing("plain");
        parent.values.put("a", decorated);
        parent.values.put("b", plain);

        RenderContext context = new RenderContext();
        JLabel idChip = new JLabel("Q208045");
        // The same mechanism cards use — here non-null only for one target, so the other
        // reference stays undecorated (scoped to non-null).
        context.setCardDecorator(t -> t == decorated ? idChip : null);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(parent, ViewConfig.all(DynamicThing.class), context, false));

        assertTrue(contains(card[0], idChip),
                "a reference whose decoration is non-null carries the identity chip");
    }

    private static boolean contains(Component root, Component target) {
        if (root == target) return true;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (contains(child, target)) return true;
            }
        }
        return false;
    }

    private static <T extends Component> T find(
            Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int count(Component root, Class<?> type) {
        int result = type.isInstance(root) ? 1 : 0;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                result += count(child, type);
            }
        }
        return result;
    }

    private static class DynamicThing
            extends ViewableAdapter implements DynamicFields {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final String id;
        private FieldSchema schema;

        private DynamicThing() {
            this("dynamic");
        }

        private DynamicThing(String id) {
            this.id = id;
        }

        @Override public Map<String, Object> dynamicFieldValues() {
            return values;
        }

        @Override public FieldSchema dynamicFieldSchema() {
            return schema;
        }

        @Override public String getIdentifier() {
            return id;
        }

        @Override public String getDisplayName() {
            return id;
        }
    }
}
