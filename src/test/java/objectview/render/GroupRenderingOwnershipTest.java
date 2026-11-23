package objectview.render;

import org.junit.jupiter.api.Test;
import objectview.ViewableAdapter;
import objectview.group.DefaultViewableGroup;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Forces one production owner for the group outline/member composition. */
class GroupRenderingOwnershipTest {

    @Test void legacyGroupViewIsOnlyAFacadeOverTheSharedComposition() {
        Group root = new Group("All");
        root.getOrCreateChild("Ennead").addMember(new Item("Ra"), false);

        GroupView view = new GroupView(root);

        assertEquals(1, descendants(view, GroupMembersView.class),
                "GroupView must delegate to the same group/member component used by "
                        + "the workbenches");
    }

    @Test void onlyGroupMembersViewConstructsTheGroupTree() throws Exception {
        Path sources = Path.of("src/main/java");
        Map<String, Integer> constructors = new LinkedHashMap<>();
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String text = Files.readString(file);
                int count = occurrences(text, "new GroupTreeView(");
                if (count > 0) {
                    constructors.put(sources.relativize(file).toString(), count);
                }
            }
        }

        assertEquals(Map.of(
                        "objectview/render/GroupMembersView.java", 1),
                constructors,
                "GroupMembersView owns the one production group tree; callers must "
                        + "supply member rendering and actions to it");
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(part, at)) >= 0; at += part.length()) {
            count++;
        }
        return count;
    }

    private static int descendants(Container root, Class<?> type) {
        int count = type.isInstance(root) ? 1 : 0;
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) count += descendants(nested, type);
            else if (type.isInstance(child)) count++;
        }
        return count;
    }

    private static final class Group extends DefaultViewableGroup<Item, Group> {
        private Group(String name) { super(name); }
        @Override protected Group newChild(String name) { return new Group(name); }
    }

    private static final class Item extends ViewableAdapter {
        private final String name;
        private Item(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
