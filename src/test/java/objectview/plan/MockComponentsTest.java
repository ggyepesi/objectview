package objectview.plan;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Link;
import objectview.annotations.Reference;
import objectview.field.FieldPath;
import objectview.media.MediaValue;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.ViewConfigEditor;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forcing tests for the mock component contract, not merely its text trace. */
class MockComponentsTest {

    @Test void componentsRetainRepresentationOccurrenceTargetAndDiagnostics() {
        Child ada = new Child("Ada");
        Parent parent = new Parent(ada);
        MockSink.MockObject root = render(parent, config(), Disclosure.INITIAL);

        assertSame(parent, root.target());
        assertEquals(Representation.OBJECT, root.representation());
        assertEquals("title", root.captionAt().path().dotted());

        MockSink.MockText text = assertInstanceOf(MockSink.MockText.class,
                child(root, "description"));
        assertEquals(Representation.TEXT, text.representation());
        assertEquals("description", text.occurrence().path().dotted());
        assertEquals("A compiler pioneer", text.value());

        MockSink.MockText disabled = assertInstanceOf(MockSink.MockText.class,
                child(root, "enabled"));
        assertEquals(Boolean.FALSE, disabled.value());

        MockSink.MockLink link = assertInstanceOf(MockSink.MockLink.class,
                child(root, "site"));
        assertEquals(Representation.LINK, link.representation());

        MockSink.MockMedia media = assertInstanceOf(MockSink.MockMedia.class,
                child(root, "portrait"));
        assertEquals(Representation.MEDIA, media.representation());

        MockSink.MockNavigation navigation = assertInstanceOf(MockSink.MockNavigation.class,
                child(root, "leader"));
        assertSame(ada, navigation.target());
        assertEquals(Representation.REFERENCE, navigation.representation());
        assertEquals("leader.name", navigation.captionAt().path().dotted());

        MockSink.MockCollection members = assertInstanceOf(MockSink.MockCollection.class,
                child(root, "members"));
        assertSame(parent.members, members.decision().value());
        assertEquals(1, members.size());
        assertFalse(members.expanded());
        assertInstanceOf(MockSink.MockDeferred.class, members.members().get(0));

        MockSink.MockSkip absent = assertInstanceOf(MockSink.MockSkip.class,
                child(root, "missing"));
        assertEquals(RenderSink.SkipReason.ABSENT, absent.reason());
        MockSink.MockSkip off = assertInstanceOf(MockSink.MockSkip.class,
                child(root, "unticked"));
        assertEquals(RenderSink.SkipReason.OFF, off.reason());
    }

    @Test void expandCollapseAndReexpandChangeOnlyTheLazyCollectionBody() {
        Child ada = new Child("Ada");
        Parent parent = new Parent(ada);
        MockDisclosure disclosure = new MockDisclosure();

        MockSink.MockCollection folded = collection(render(parent, config(), disclosure));
        disclosure.expand(parent.members);
        MockSink.MockCollection opened = collection(render(parent, config(), disclosure));
        disclosure.collapse(parent.members);
        MockSink.MockCollection foldedAgain = collection(render(parent, config(), disclosure));
        disclosure.expand(parent.members);
        MockSink.MockCollection reopened = collection(render(parent, config(), disclosure));

        assertFalse(folded.expanded());
        assertFalse(foldedAgain.expanded());
        assertTrue(opened.expanded());
        assertTrue(reopened.expanded());
        MockSink.MockObject member = assertInstanceOf(
                MockSink.MockObject.class, opened.members().get(0));
        MockSink.MockObject sameMember = assertInstanceOf(
                MockSink.MockObject.class, reopened.members().get(0));
        assertSame(ada, member.target());
        assertSame(ada, sameMember.target());
        assertEquals(member.caption(), sameMember.caption());
    }

    @Test void connectedConfigSuppressionAndRestorationDriveTheMockComponents()
            throws Exception {
        Child ada = new Child("Ada");
        Parent parent = new Parent(ada);
        ViewConfig configured = ViewConfig.of(Parent.class);
        configured.setAllFields(false);
        configured.addField("members", ticks("name"));

        ViewConfigEditor[] editor = new ViewConfigEditor[1];
        ViewConfig[] suppressedConfig = new ViewConfig[1];
        SwingUtilities.invokeAndWait(() -> {
            editor[0] = new ViewConfigEditor(configured, parent);
            editor[0].setConnectedFieldSelection(true);
            assertTrue(editor[0].uncheckFieldPath(FieldPath.of("members")));
            suppressedConfig[0] = editor[0].getConfig();
        });

        MockSink.MockSkip suppressed = assertInstanceOf(MockSink.MockSkip.class,
                child(render(parent, suppressedConfig[0], Disclosure.INITIAL), "members"));
        assertEquals(RenderSink.SkipReason.OFF, suppressed.reason(),
                "suppressed checked descendants must not leak into rendering");

        ViewConfig[] restoredConfig = new ViewConfig[1];
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(editor[0].checkFieldPath(FieldPath.of("members")));
            restoredConfig[0] = editor[0].getConfig();
        });
        MockDisclosure disclosure = new MockDisclosure();
        disclosure.expand(parent.members);
        MockSink.MockCollection restored = assertInstanceOf(MockSink.MockCollection.class,
                child(render(parent, restoredConfig[0], disclosure), "members"));
        MockSink.MockObject member = assertInstanceOf(
                MockSink.MockObject.class, restored.members().get(0));
        assertSame(ada, member.target());
        assertEquals("Ada", member.caption());
    }

    @Test void connectedConfigAddsAncestorsAndClearRemovesTheRenderedBranch()
            throws Exception {
        Child ada = new Child("Ada");
        Parent parent = new Parent(ada);
        ViewConfig empty = ViewConfig.of(Parent.class);
        empty.setAllFields(false);

        ViewConfigEditor[] editor = new ViewConfigEditor[1];
        ViewConfig[] selectedConfig = new ViewConfig[1];
        SwingUtilities.invokeAndWait(() -> {
            editor[0] = new ViewConfigEditor(empty, parent);
            editor[0].setConnectedFieldSelection(true);
            assertTrue(editor[0].checkFieldPath(FieldPath.of("members", "name")));
            selectedConfig[0] = editor[0].getConfig();
        });

        MockDisclosure disclosure = new MockDisclosure();
        disclosure.expand(parent.members);
        assertInstanceOf(MockSink.MockCollection.class,
                child(render(parent, selectedConfig[0], disclosure), "members"));

        ViewConfig[] clearedConfig = new ViewConfig[1];
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(editor[0].clearNestedSelection(FieldPath.of("members")));
            assertFalse(editor[0].checkFieldPath(FieldPath.of("members")),
                    "an object branch cannot render without a nested value");
            clearedConfig[0] = editor[0].getConfig();
        });
        MockSink.MockSkip cleared = assertInstanceOf(MockSink.MockSkip.class,
                child(render(parent, clearedConfig[0], disclosure), "members"));
        assertEquals(RenderSink.SkipReason.OFF, cleared.reason());
    }

    private static MockSink.MockCollection collection(MockSink.MockObject root) {
        return assertInstanceOf(MockSink.MockCollection.class, child(root, "members"));
    }

    private static MockSink.Mock child(MockSink.MockObject root, String at) {
        return root.children().stream().filter(mock -> at.equals(mock.at()))
                .findFirst().orElseThrow();
    }

    private static MockSink.MockObject render(Parent value, ViewConfig config,
                                               Disclosure disclosure) {
        MockSink sink = new MockSink();
        ViewConfig literal = ViewConfigDesugar.literal(config, TypeShape.ofClass(Parent.class));
        new RenderExecutor(new PlanResolver(), ignored -> null, candidate -> candidate instanceof Child,
                disclosure).render(value, literal, sink);
        return assertInstanceOf(MockSink.MockObject.class, sink.root());
    }

    private static ViewConfig config() {
        ViewConfig root = ticks(
                "title", "description", "enabled", "site", "portrait", "missing");
        root.addField("leader", ticks("name"));
        root.addField("members", ticks("name"));
        return root;
    }

    private static ViewConfig ticks(String... fields) {
        ViewConfig config = ViewConfig.leaf();
        for (String field : fields) config.addField(field, ViewConfig.leaf());
        return config;
    }

    private static final class Parent extends ViewableAdapter {
        @DisplayField private final String title = "Engineers";
        private final String description = "A compiler pioneer";
        private final boolean enabled = false;
        @Link private final String site = "https://example.org";
        private final Portrait portrait = new Portrait();
        @Reference private final Child leader;
        @Reference private final List<Child> members;
        @SuppressWarnings("unused") private final String missing = null;
        @SuppressWarnings("unused") private final String unticked = "must not be read";

        private Parent(Child leader) {
            this.leader = leader;
            this.members = List.of(leader);
        }

        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }

    private static final class Child extends ViewableAdapter {
        @DisplayField private final String name;
        private Child(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Portrait implements MediaValue {
        @Override public String mediaLabel() { return "portrait"; }
        @Override public String mediaUrl() { return "https://example.org/portrait.png"; }
        @Override public boolean mediaSvg() { return false; }
        @Override public String toString() { return "portrait"; }
    }
}
