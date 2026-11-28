package objectview.demo;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.field.FieldPath;
import objectview.render.ReferenceRow;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiViewTabNavigationTest {

    @Test void aHostThatDoesNotOptInKeepsAllFiveSectionsSideBySide() {
        EdtTests.onEdt(() -> {
            MultiView multi = new MultiView();
            IntStream.rangeClosed(1, 5).forEach(i -> {
                Item item = new Item("Type" + i);
                multi.addSection(item.typeName(), Item.class, List.of(item));
            });
            multi.build(1);

            assertNull(find(multi, JTabbedPane.class, "objectview.sections.tabs"));
            assertNull(find(multi, JSpinner.class, null));
            assertEquals(5, multi.visibleSectionTitles().size());
        });
    }

    @Test void fourSectionsPerTabByDefaultAndRegroupingKeepsTheSelectedSection() {
        EdtTests.onEdt(() -> {
            MultiView multi = new MultiView();
            multi.sectionsPerTab(MultiView.DEFAULT_SECTIONS_PER_TAB);
            IntStream.rangeClosed(1, 5).forEach(i -> {
                Item item = new Item("Type" + i);
                multi.addSection(item.typeName(), Item.class, List.of(item));
            });
            multi.build(1);

            JTabbedPane tabs = find(multi, JTabbedPane.class, "objectview.sections.tabs");
            JSpinner count = find(multi, JSpinner.class, null);
            assertNotNull(tabs);
            assertEquals(4, count.getValue());
            assertEquals(List.of("Type1 · Type2 · Type3 · Type4", "Type5"), titles(tabs));
            tabs.setSelectedIndex(1);

            count.setValue(2);

            assertEquals(List.of("Type1 · Type2", "Type3 · Type4", "Type5"), titles(tabs));
            assertEquals(2, tabs.getSelectedIndex());
            assertFalse(back(multi).isEnabled());
            multi.visibleSections(Set.of("Type1", "Type5"));
            assertNull(find(multi, JTabbedPane.class, "objectview.sections.tabs"),
                    "one page needs no tabs");
        });
    }

    @Test void followingAnInstanceReferenceRevealsItsTabAndBackRestoresTheSourceScroll() {
        EdtTests.onEdt(() -> {
            Fixture f = new Fixture();
            // The card has a real viewport and a real reference row; no frame is needed.
            Point before = new Point(0, 140);
            f.sourceViewport.setViewPosition(before);
            click(f.sourceLink);

            assertEquals(1, f.tabs.getSelectedIndex());
            JButton back = back(f.multi);
            assertNotNull(back);
            assertEquals("← Back to One", back.getText());

            f.sourceViewport.setViewPosition(new Point(0, 20));
            back.doClick();

            assertEquals(0, f.tabs.getSelectedIndex());
            assertEquals(before, f.sourceViewport.getViewPosition());
            assertFalse(back(f.multi).isEnabled(), "Back is consumed");
        });
    }

    @Test void aSecondCrossTabLinkReplacesBackAndManualSelectionClearsIt() {
        EdtTests.onEdt(() -> {
            Fixture f = new Fixture();
            click(f.sourceLink);
            click(f.secondLink);

            assertEquals(2, f.tabs.getSelectedIndex());
            assertEquals("← Back to Two", back(f.multi).getText());
            back(f.multi).doClick();
            assertEquals(1, f.tabs.getSelectedIndex());
            assertFalse(back(f.multi).isEnabled());

            click(f.secondLink);
            f.tabs.setSelectedIndex(0);
            assertFalse(back(f.multi).isEnabled());
            assertTrue(f.multi.context().focusTopLevel(f.third));
            assertEquals(2, f.tabs.getSelectedIndex());
            assertFalse(back(f.multi).isEnabled(), "a programmatic focus is not a link");
            f.multi.sectionsPerTab(2);
            f.tabs.setSelectedIndex(0);
            click(f.sourceLink);
            assertEquals(0, f.tabs.getSelectedIndex());
            assertTrue(back(f.multi).isEnabled(), "the same Back serves in-tab navigation");
            assertEquals("← Back", back(f.multi).getText());
            back(f.multi).doClick();
            assertEquals(0, f.tabs.getSelectedIndex());
            assertFalse(back(f.multi).isEnabled());

        });
    }

    @Test void followingAHiddenTargetReturnsToTheSourceEvenWhenTabsAreRegrouped() {
        EdtTests.onEdt(() -> {
            Fixture f = new Fixture();
            f.multi.visibleSections(Set.of("One"));
            assertNull(find(f.multi, JTabbedPane.class, "objectview.sections.tabs"));
            Point before = new Point(0, 120);
            f.sourceViewport.setViewPosition(before);

            click(f.sourceLink);

            assertEquals(Set.of("One", "Two"), f.multi.visibleSectionTitles());
            assertEquals("Two", f.tabs.getTitleAt(f.tabs.getSelectedIndex()));
            JButton back = back(f.multi);
            assertEquals("← Back to One", back.getText());
            f.sourceViewport.setViewPosition(new Point(0, 20));
            back.doClick();

            assertEquals("One", f.tabs.getTitleAt(f.tabs.getSelectedIndex()));
            assertEquals(before, f.sourceViewport.getViewPosition());
            assertFalse(back.isEnabled());
            assertEquals(Set.of("One", "Two"), f.multi.visibleSectionTitles(),
                    "Back returns to the source without changing Classes shown again");
        });
    }

    @Test void enclosingTabRevealsBeforeTheTargetsNestedPresenter() {
        EdtTests.onEdt(() -> {
            Fixture f = new Fixture(true);
            click(f.sourceLink);
            assertEquals(List.of(1), f.pageSeenByNestedPresenter);
            assertTrue(back(f.multi).isEnabled());
        });
    }

    @Test void explicitSearchHitNavigationRevealsThePageWithoutCreatingBack() {
        EdtTests.onEdt(() -> {
            MultiView multi = new MultiView();
            Item moon = new Item("Moon");
            multi.sectionsPerTab(1);
            multi.addSection("Planet", Item.class, List.of(new Item("Planet")));
            multi.addSection("Moon", Item.class, List.of(moon));
            multi.build(1);

            var tabs = find(multi, JTabbedPane.class, "objectview.sections.tabs");
            var moonView = multi.sectionViews().get(1);
            moonView.cardList().getVirtualList().buildIfNeeded(moon);
            var moonSearch = moonView.search();
            moonSearch.runCoordinatedSearch("Moon");
            assertEquals(0, tabs.getSelectedIndex(), "searching does not move the reader");
            assertNull(tabs.getIconAt(0), "only the tab with a match is marked");
            assertNotNull(tabs.getIconAt(1));
            assertEquals("Moon", tabs.getTitleAt(1), "the mark does not change the section label");

            JButton nextHit = findButtonWithCaption(moonSearch, ">");
            assertNotNull(nextHit);
            nextHit.doClick();
            assertEquals(1, tabs.getSelectedIndex());
            assertFalse(back(multi).isEnabled());
            moonSearch.runCoordinatedSearch("No such moon");
            assertNull(tabs.getIconAt(1), "a later search with no matching cards clears the mark");
            moonSearch.runCoordinatedSearch("Moon");
            assertNotNull(tabs.getIconAt(1));
            moonSearch.runCoordinatedSearch("");
            assertNull(tabs.getIconAt(1), "clearing search removes its marks");
        });
    }

    @Test void aPageIsMarkedIfAnyOfItsSectionsHasHitsAndMarksFollowRegrouping() {
        EdtTests.onEdt(() -> {
            MultiView multi = new MultiView();
            multi.sectionsPerTab(2);
            multi.addSection("Planet", Item.class, List.of(new Item("Earth")));
            multi.addSection("Moon", Item.class, List.of(new Item("Luna")));
            multi.addSection("Comet", Item.class, List.of(new Item("Halley")));
            multi.build(1);
            var tabs = find(multi, JTabbedPane.class, "objectview.sections.tabs");
            var lunarSearch = multi.sectionViews().get(1).search();
            lunarSearch.runCoordinatedSearch("Luna");
            assertNotNull(tabs.getIconAt(0));
            assertNull(tabs.getIconAt(1));

            multi.sectionsPerTab(1);
            assertNull(tabs.getIconAt(0));
            assertNotNull(tabs.getIconAt(1));
            assertNull(tabs.getIconAt(2));
        });
    }

    private static JButton findButtonWithCaption(Container root, String caption) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && caption.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton result = findButtonWithCaption(nested, caption);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static final class Fixture {
        final MultiView multi = new MultiView();
        final Item one = new Item("One");
        final Item two = new Item("Two");
        final Item third = new Item("Three");
        final ReferenceRow sourceLink = link(two, "two");
        final ReferenceRow secondLink = link(third, "three");
        final JPanel source = new JPanel(null);
        final JViewport sourceViewport = new JViewport();
        final JTabbedPane tabs;
        final List<Integer> pageSeenByNestedPresenter = new java.util.ArrayList<>();

        Fixture() { this(false); }

        Fixture(boolean observeNestedReveal) {
            source.add(sourceLink);
            sourceLink.setBounds(0, 150, 200, 30);
            sourceViewport.setView(source);
            sourceViewport.setViewSize(new Dimension(200, 400));
            sourceViewport.setExtentSize(new Dimension(200, 100));
            JPanel second = new JPanel();
            second.add(secondLink);
            secondLink.setSize(200, 30);
            JPanel last = new JPanel();
            JLabel finalCard = new JLabel("Three");
            last.add(finalCard);
            multi.sectionsPerTab(1);
            multi.addSection("One", sourceViewport, List.of(one));
            multi.addSection("Two", second, List.of(two));
            multi.addSection("Three", last, List.of(third));
            if (observeNestedReveal) {
                multi.context().registerTopLevelRevealer(two,
                        () -> pageSeenByNestedPresenter.add(
                                find(multi, JTabbedPane.class, "objectview.sections.tabs").getSelectedIndex()));
            }
            multi.build(1);
            multi.context().registerTopLevel(two, secondLink);
            multi.context().registerTopLevel(third, finalCard);
            tabs = find(multi, JTabbedPane.class, "objectview.sections.tabs");
        }

        private ReferenceRow link(Item target, String field) {
            return new ReferenceRow(field, FieldPath.of(field), target, multi.context(),
                    null, null, false, true);
        }
    }

    private static void click(ReferenceRow link) {
        Point point = new Point(75, 12);
        link.beginSelection(point);
        link.endSelection(point);
    }

    private static List<String> titles(JTabbedPane tabs) {
        return IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt).toList();
    }

    private static JButton back(Container root) {
        return find(root, JButton.class, "objectview.navigation.back");
    }

    private static <T extends Component> T find(Container root, Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && (name == null || name.equals(child.getName()))) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                T found = find(nested, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Item extends ViewableAdapter {
        private final String type;
        Item(String type) { this.type = type; }
        @Override public String getIdentifier() { return type; }
        @Override public String getDisplayName() { return type; }
        @Override public String typeName() { return type; }
        @Override public String identityTypeName() { return type; }
    }
}
