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

    @Test void fieldsSharedByEveryMemberExtendToTheGroup() {
        Person odin = new Person("Q43610", "Odin");
        RelatedItem hugin = new RelatedItem(
                "Q59457190", "Hugin", odin, List.of("raven", "flying"));
        RelatedItem munin = new RelatedItem(
                "Q59457345", "Munin", odin, List.of("flying", "black"));
        RelatedGroup group = new RelatedGroup("Hugin and Munin");
        group.addMember(hugin, false);
        group.addMember(munin, false);

        FieldSet fields = FieldSet.of(group);

        assertEquals(odin, fields.read("owner"));
        assertEquals(List.of("flying"), fields.read("traits"));
        assertTrue(ViewableGroupFields.isMemberDerived(fields.field("owner")));
        assertTrue(ViewableGroupFields.isMemberDerived(fields.field("traits")));
    }

    /** Reported: reading a 40,000-member group's caption scanned every member's fields
     *  (180 ms per read), because the shared-field projection was built eagerly. Only
     *  listing the fields or reading a field the group itself lacks may scan members. */
    @Test void aGroupsCaptionAndMembersAreReadWithoutScanningItsMembers() {
        int[] scans = { 0 };
        RelatedGroup group = new RelatedGroup("Ravens");
        for (int index = 0; index < 3; index++) {
            group.addMember(new RelatedItem("Q" + index, "Raven " + index,
                    new Person("Q43610", "Odin"), List.of("flying")) {
                @Override public FieldSet fields() {
                    scans[0]++;
                    return super.fields();
                }
            }, false);
        }

        FieldSet fields = FieldSet.of(group);
        fields.read(ViewableContractFieldSet.DISPLAY_KEY);
        fields.read("members");
        fields.has(ViewableContractFieldSet.IDENTITY_KEY);
        assertEquals(0, scans[0], "caption, members and contract keys need no member scan");

        assertEquals("flying", ((List<?>) fields.read("traits")).get(0));
        assertTrue(scans[0] > 0, "a member-derived field is projected when it is read");
    }

    @Test void aMissingOrDifferentMemberValueDoesNotExtendToTheGroup() {
        Person odin = new Person("Q43610", "Odin");
        Person thor = new Person("Q43611", "Thor");
        RelatedGroup different = new RelatedGroup("Different owners");
        different.addMember(new RelatedItem(
                "Q1", "one", odin, List.of("raven")), false);
        different.addMember(new RelatedItem(
                "Q2", "two", thor, List.of("raven")), false);
        RelatedGroup missing = new RelatedGroup("Missing owner");
        missing.addMember(new RelatedItem(
                "Q3", "three", odin, List.of("raven")), false);
        missing.addMember(new RelatedItem(
                "Q4", "four", null, List.of("raven")), false);

        assertFalse(FieldSet.of(different).has("owner"));
        assertFalse(FieldSet.of(missing).has("owner"));
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
        @objectview.annotations.Role(objectview.field.FieldRole.IDENTITY)
        private final String id;
        @objectview.annotations.DisplayField
        private final String label;
        private Item(String id, String label) {
            this.id = id;
            this.label = label;
        }
        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return label; }
    }

    private static final class Person extends ViewableAdapter {
        private final String id;
        private final String label;
        private Person(String id, String label) {
            this.id = id;
            this.label = label;
        }
        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return label; }
        @Override public String identityTypeName() { return "Person"; }
    }

    private static class RelatedItem extends ViewableAdapter {
        private final String id;
        private final String label;
        private final Person owner;
        private final List<String> traits;
        private RelatedItem(
                String id, String label, Person owner, List<String> traits) {
            this.id = id;
            this.label = label;
            this.owner = owner;
            this.traits = traits;
        }
        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return label; }
    }

    private static final class RelatedGroup
            extends DefaultViewableGroup<RelatedItem, RelatedGroup> {
        private RelatedGroup(String name) { super(name); }
        @Override protected RelatedGroup newChild(String name) {
            return new RelatedGroup(name);
        }
    }
}
