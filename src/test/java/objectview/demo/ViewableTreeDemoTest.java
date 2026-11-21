package objectview.demo;

import objectview.group.GroupNode;
import objectview.group.ViewableGroup;
import objectview.field.FieldSet;
import objectview.render.GroupMembersView;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The runnable tree example must keep demonstrating the production group contract. */
class ViewableTreeDemoTest {

    @Test void demoShowsCountsAlternativeHierarchiesAndSelectionDrivenMembers()
            throws Exception {
        ViewableTreeDemo.DemoData data = ViewableTreeDemo.sampleData();

        assertEquals(7, data.root().getMembers().size());
        assertEquals(7, data.byKind().getMembers().size());
        assertEquals(6, data.byParent().getMembers().size());
        assertEquals("Planets (3)", new GroupNode(data.planets()).toString());
        assertEquals(ViewableGroup.Role.FACET, data.byKind().getRole());
        assertEquals(ViewableGroup.Role.BUCKET, data.moons().getRole());
        FieldSet moonFields = FieldSet.of(data.moons());
        assertEquals("Bodies classified as moons", moonFields.read("description"));
        assertEquals("kind = moon", moonFields.read("groupingRule"));
        assertTrue(moonFields.has("members"),
                "group metadata must not replace the standard member references");

        GroupMembersView[] rendered = new GroupMembersView[1];
        SwingUtilities.invokeAndWait(() ->
                rendered[0] = ViewableTreeDemo.createView(data));
        assertSame(data.root(), rendered[0].groups().selectedGroup());
        SwingUtilities.invokeAndWait(() ->
                assertTrue(rendered[0].selectGroup(data.moons(), true)));
        assertSame(data.moons(), rendered[0].groups().selectedGroup());
    }
}
