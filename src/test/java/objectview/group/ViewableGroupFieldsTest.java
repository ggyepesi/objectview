package objectview.group;

import objectview.ViewableAdapter;
import objectview.field.FieldRef;
import objectview.field.FieldSet;
import objectview.field.ViewableContractFieldSet;
import objectview.plan.TypeShape;
import objectview.plan.ViewDefaults;
import objectview.render.Card;
import objectview.view.SearchableView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A group is an ordinary Viewable with one schema, whatever backs the group. */
class ViewableGroupFieldsTest {

    @Test void reflectedAndAdapterGroupsPublishTheSameObjectViewFields() {
        ReflectedGroup reflected = new ReflectedGroup("Reflected");
        AdapterGroup adapted = new AdapterGroup("Adapted");
        Item member = new Item("Q1", "member");
        reflected.addMember(member, false);
        adapted.member(member);

        FieldSet reflectedFields = FieldSet.of(reflected);
        FieldSet adaptedFields = FieldSet.of(adapted);

        assertEquals(names(reflected.fields()), names(reflectedFields));
        assertEquals(names(adapted.fields()), names(adaptedFields));
        assertEquals(names(reflectedFields), names(adaptedFields));
        assertEquals(List.of("members", "keyRef", "children", "parent", "role",
                        ViewableContractFieldSet.DISPLAY_KEY), names(adaptedFields));
        assertEquals(List.of(member), ((java.util.Map<?, ?>) adaptedFields.read("members"))
                .values().stream().toList());
        assertEquals("Adapted", adaptedFields.read(ViewableContractFieldSet.DISPLAY_KEY));
        assertFalse(adaptedFields.field("members").structural(),
                "membership is visible group data");
        assertFalse(adaptedFields.field("children").structural(),
                "the ordinary card owns the visible subgroup count and values");
        assertEquals("Members", adaptedFields.field("members").label());
        assertEquals("Subgroups", adaptedFields.field("children").label());
    }

    @Test void anOrdinarySearchableViewCanRenderAGroup() {
        AdapterGroup group = new AdapterGroup("Olympians");
        group.member(new Item("Q34201", "Zeus"));

        SearchableView view = SearchableView.builder(List.of(group)).build();
        TypeShape shape = TypeShape.of(group, null, null);

        assertNotNull(view.search());
        assertNotNull(view.search().viewEditor());
        assertTrue(FieldSet.of(group).has("members"));
        assertEquals(names(FieldSet.of(group)),
                shape.fields().stream().map(FieldRef::name).toList());
        assertTrue(ViewDefaults.newView(shape).hasField("members"));
        assertTrue(ViewDefaults.newView(shape).hasField("children"));
        assertTrue(new Card(group, null, false).hasRenderedConfiguredContent());
    }

    private static List<String> names(FieldSet fields) {
        return fields.fields().stream().map(FieldRef::name).toList();
    }

    private static final class AdapterGroup extends ViewableGroupAdapter {
        private AdapterGroup(String name) { super(name, name); }
        private void member(Item item) { putMember(item); }
    }

    private static final class ReflectedGroup
            extends DefaultViewableGroup<Item, ReflectedGroup> {
        private ReflectedGroup(String name) { super(name); }
        @Override protected ReflectedGroup newChild(String name) {
            return new ReflectedGroup(name);
        }
    }

    private static final class Item extends ViewableAdapter {
        private final String id;
        private final String label;
        private Item(String id, String label) {
            this.id = id;
            this.label = label;
        }
        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return label; }
    }
}
