package objectview.render;

import objectview.ViewableAdapter;
import objectview.field.DynamicFields;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static final class DynamicThing
            extends ViewableAdapter implements DynamicFields {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final String id;

        private DynamicThing() {
            this("dynamic");
        }

        private DynamicThing(String id) {
            this.id = id;
        }

        @Override public Map<String, Object> dynamicFieldValues() {
            return values;
        }

        @Override public String getIdentifier() {
            return id;
        }

        @Override public String getDisplayName() {
            return id;
        }
    }
}
