package objectview.search;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.demo.MultiView;
import objectview.group.DefaultViewableGroup;
import objectview.field.FieldPath;
import objectview.field.FieldProperties;
import objectview.render.Card;
import objectview.render.GroupView;
import objectview.render.GroupTreeView;
import objectview.render.TextBlock;
import objectview.view.SearchableView;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.VirtualizedCardList;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The empty-field presentation choice is an ordinary persisted View setting (#568). */
class EmptyFieldsToggleTest {

    @Test void aReaderCanHideEmptySelectedFieldsAndApplyThatChoice() {
        EdtTests.onEdt(() -> {
            Item item = new Item();
            AtomicReference<SearchPanel.ConfigState> saved = new AtomicReference<>();
            SearchableView view = SearchableView.builder(List.of(item)).sample(item)
                    .configListener(saved::set).build();

            JCheckBox box = find(view.search().viewEditor());
            assertNotNull(box, "View Config offers the empty-field choice");
            assertTrue(box.isSelected(), "compatible default: show empty fields");

            box.doClick();
            view.search().applyView();

            assertEquals(Boolean.FALSE, saved.get().view().emptyFieldsVisible());
        });
    }

    @Test void applyActuallyRemovesEmptyRowsAndTheirDisclosureFromTheCard() {
        EdtTests.onEdt(() -> {
            Item item = new Item();
            SearchableView view = SearchableView.builder(List.of(item)).sample(item).build();

            Card shown = card(view, item);
            assertTrue(text(shown).hasMatchingRow(
                    FieldPath.of("note"), List.of(), true));
            assertNotNull(field(shown, "values"));
            assertNotNull(field(shown, "empty"));
            assertNotNull(field(shown, "populated"));

            find(view.search().viewEditor()).doClick();
            view.search().applyView();

            Card hidden = card(view, item);
            TextBlock remaining = find(hidden, TextBlock.class);
            assertTrue(remaining == null || !remaining.hasMatchingRow(
                    FieldPath.of("note"), List.of(), true));
            assertNull(field(hidden, "values"));
            assertNull(field(hidden, "empty"));
            assertNotNull(field(hidden, "populated"));
        });
    }

    @Test void allTabsToggleRepaintsGroupMembersAndOpenGroupDetailsWithoutASearchEngine() {
        EdtTests.onEdt(() -> {
            Item item = new Item();
            Group root = new Group("Group");
            root.addMember(item, false);
            MultiView multi = new MultiView();
            GroupView group = new GroupView(root, multi.context());
            multi.addSection("Group", group, List.of(item));
            multi.build(1);
            group.getTree().getActionMap().get("showGroup").actionPerformed(
                    new ActionEvent(group.getTree(), ActionEvent.ACTION_PERFORMED, "showGroup"));
            var outline = (GroupTreeView) group.getMainPanel();
            assertTrue(outline.setGroupDetailsExpanded(root, true));
            var members = find(group, VirtualizedCardList.class);
            ViewConfig projection = ViewConfig.leaf();
            projection.addField("empty", ViewConfig.leaf());
            projection.addField("populated", ViewConfig.leaf());
            members.setViewConfigResolver(member -> projection);
            assertNotNull(field(members.buildIfNeeded(item), "empty"));
            assertNull(field(members.buildIfNeeded(item), "unselected"));
            assertNotNull(field(find(outline, Card.class), "children"));

            JCheckBox showEmpty = find(multi);
            assertTrue(showEmpty.isEnabled(), "group-only pages still offer the shared choice");
            showEmpty.doClick();

            assertNull(field(members.buildIfNeeded(item), "empty"));
            assertNull(field(find(outline, Card.class), "children"));
            assertNotNull(field(members.buildIfNeeded(item), "populated"));
            assertNull(field(members.buildIfNeeded(item), "unselected"),
                    "refreshing member cards must keep their field selection");

            showEmpty.doClick();
            assertNotNull(field(members.buildIfNeeded(item), "empty"));
            assertNotNull(field(find(outline, Card.class), "children"));
        });
    }

    @Test void allTabsChoiceSurvivesShowingAHiddenClassAndReachesNestedTabs() {
        EdtTests.onEdt(() -> {
            Item item = new Item();
            MultiView multi = new MultiView();
            multi.sectionsPerTab(1);
            SearchableView nested = SearchableView.builder(List.of(item))
                    .sample(item).renderContext(multi.context()).coordinated(true).build();
            multi.addSection("Ordinary", Item.class, List.of(item));
            multi.addDedicatedTab("Decisions", "Decisions", () -> nested, List.of(item));
            multi.visibleSections(Set.of("Ordinary"));
            multi.build(1);

            find(multi).doClick();
            multi.visibleSections(Set.of("Ordinary", "Decisions"));

            assertFalse(find(multi).isSelected(), "showing a class keeps the all-tabs choice");
            assertNull(field(card(nested, item), "empty"));
            assertNotNull(field(card(nested, item), "populated"));
        });
    }

    private static final class Group extends DefaultViewableGroup<Item, Group> {
        private Group(String name) { super(name); }
        @Override protected Group newChild(String name) { return new Group(name); }
    }

    private static Card card(SearchableView view, Item item) {
        return (Card) view.cardList().getVirtualList().ensureVisible(item);
    }

    private static TextBlock text(Container root) {
        TextBlock found = find(root, TextBlock.class);
        if (found == null) throw new AssertionError("No rendered text block");
        return found;
    }

    private static Component field(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JComponent component
                    && name.equals(component.getClientProperty(
                    FieldProperties.FIELD_NAME_PROPERTY))) return component;
            if (child instanceof Container nested) {
                Component found = field(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JCheckBox find(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JCheckBox box
                    && "Show empty fields".equals(box.getText())) return box;
            if (child instanceof Container nested) {
                JCheckBox found = find(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Item extends ViewableAdapter {
        @SuppressWarnings("unused") private final String note = "   ";
        @SuppressWarnings("unused") private final List<String> values = List.of(" ");
        @SuppressWarnings("unused") private final List<String> empty = List.of();
        @SuppressWarnings("unused") private final List<String> populated = List.of("present");
        @SuppressWarnings("unused") private final List<String> unselected = List.of("not selected");
        @Override public String getIdentifier() { return "item"; }
        @Override public String getDisplayName() { return "item"; }
    }
}
