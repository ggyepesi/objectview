package objectview.plan;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Reference;
import objectview.field.FieldPath;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.ViewConfigEditor;
import objectview.viewconfig.ViewConfigJsonIO;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A default recursive View may inherit an ancestor projection (#368), but a manual
 * configuration is a finite authored path and may continue through a cycle to any
 * requested depth (#410). Scalar references are terminal navigation values and never
 * borrow a target projection.
 */
class RecursiveFieldsInheritTest {

    static final class Office extends ViewableAdapter {
        @DisplayField private final String name;
        private final String country;
        @Reference Office predecessor;
        @Reference final List<Office> replaces = new ArrayList<>();

        Office(String name, String country) {
            this.name = name;
            this.country = country;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private final Office president = new Office("President", "Germany");
    private final Office emperor = new Office("Emperor", "German Empire");
    private final Office king = new Office("King of Prussia", "Prussia");

    RecursiveFieldsInheritTest() {
        president.predecessor = emperor;
        emperor.predecessor = king;
        president.replaces.add(emperor);
        emperor.replaces.add(king);
    }

    private static TypeShape shape() {
        return TypeShape.ofClass(Office.class);
    }

    private static ViewConfig ticks(String... names) {
        ViewConfig config = ViewConfig.leaf();
        for (String name : names) config.addField(name, ViewConfig.leaf());
        return config;
    }

    @Test void aDefaultCollectionRecursesButAScalarReferenceIsTerminal() {
        ViewConfig defaults = ViewDefaults.newView(shape());

        assertNull(defaults.getFieldConfig("predecessor").inheritedFrom(),
                "a scalar reference is one navigation value, not a nested projection");
        assertTrue(defaults.getFieldConfig("predecessor").hasField("name"),
                "its standard display projection still supplies the link caption");
        assertSame(defaults, defaults.getFieldConfig("replaces").inheritedFrom(),
                "a default collection may reuse the root projection indefinitely");
    }

    @Test void anExplicitConfigWinsAtEveryDepthAlongACycle() {
        ViewConfig root = ticks("name");
        ViewConfig first = ticks("country");
        ViewConfig second = ticks("name");
        ViewConfig third = ticks("country");
        second.addField("replaces", third);
        first.addField("replaces", second);
        root.addField("replaces", first);

        ViewConfig literal = ViewConfigDesugar.literal(root, shape());

        ViewConfig l1 = literal.getFieldConfig("replaces");
        ViewConfig l2 = l1.getFieldConfig("replaces");
        ViewConfig l3 = l2.getFieldConfig("replaces");
        assertNull(l1.inheritedFrom());
        assertTrue(l1.hasField("country"));
        assertTrue(l2.hasField("name"));
        assertTrue(l3.hasField("country"));
    }

    @Test void theEditorCanAuthorArbitraryFurtherOccurrencesLazily() throws Exception {
        ViewConfig empty = ViewConfig.of(Office.class);
        empty.setAllFields(false);
        ViewConfig[] emitted = new ViewConfig[1];

        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(empty, president);
            editor.setConnectedFieldSelection(true);
            editor.setInheritsRecursion(true);
            assertTrue(editor.checkFieldPath(FieldPath.parse(
                    "replaces.replaces.replaces.country")));
            emitted[0] = editor.getConfig();
        });

        ViewConfig at = emitted[0];
        for (int i = 0; i < 3; i++) {
            at = at.getFieldConfig("replaces");
            assertTrue(at != null && at.inheritedFrom() == null,
                    "each manually authored occurrence owns its finite config");
        }
        assertTrue(at.hasField("country"));
    }

    @Test void merelyInspectingAnInheritedBranchDoesNotMaterializeIt() throws Exception {
        ViewConfig defaults = ViewDefaults.newView(shape());
        ViewConfig[] emitted = new ViewConfig[1];

        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(defaults, president);
            editor.setConnectedFieldSelection(true);
            editor.setInheritsRecursion(true);
            editor.setSelectedPath(FieldPath.parse("replaces.country"));
            emitted[0] = editor.getConfig();
        });

        assertSame(emitted[0], emitted[0].getFieldConfig("replaces").inheritedFrom(),
                "opening rows is inspection, not a configuration edit");
    }

    @Test void editingAnInheritedOccurrenceMakesThatOccurrenceExplicit() throws Exception {
        ViewConfig defaults = ViewDefaults.newView(shape());
        ViewConfig[] emitted = new ViewConfig[1];

        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(defaults, president);
            editor.setConnectedFieldSelection(true);
            editor.setInheritsRecursion(true);
            assertTrue(editor.uncheckFieldPath(FieldPath.parse("replaces.country")));
            emitted[0] = editor.getConfig();
        });

        ViewConfig occurrence = emitted[0].getFieldConfig("replaces");
        assertNull(occurrence.inheritedFrom());
        assertFalse(occurrence.hasField("country"));
        assertTrue(occurrence.hasField("name"));
    }

    @Test void inheritedDefaultsStillRenderOneDisclosureLevelAtATime() {
        ViewConfig defaults = ViewDefaults.newView(shape());
        MockDisclosure disclosure = new MockDisclosure().expand(president.replaces)
                .expand(emperor);
        String trace = render(defaults, disclosure);

        assertTrue(trace.contains("COLLECTION replaces (1) open"), trace);
        assertTrue(trace.contains("OBJECT replaces[0] caption=\"Emperor\" open"), trace);
        assertTrue(trace.contains("TEXT replaces[0].country \"German Empire\""), trace);
        assertTrue(trace.contains("COLLECTION replaces[0].replaces (1) folded"), trace);
    }

    @Test void copyAndPersistenceKeepAnInheritedDefaultPointingAtItsCopy() {
        ViewConfig defaults = ViewDefaults.newView(shape());
        ViewConfig copy = defaults.copy();
        assertSame(copy, copy.getFieldConfig("replaces").inheritedFrom());

        java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"),
                "recursive-fields-inherit-" + System.nanoTime() + ".json");
        try {
            ViewConfigJsonIO.save(file, defaults);
            ViewConfig loaded = ViewConfigJsonIO.fromJson(ViewConfigJsonIO.loadJson(file));
            assertSame(loaded, loaded.getFieldConfig("replaces").inheritedFrom());
        } finally {
            file.delete();
        }
    }

    @Test void aFiniteSelectionNeverGainsInheritance() {
        ViewConfig root = ticks("name");
        root.addField("replaces", ticks("country"));

        ViewConfig finite = ViewConfigDesugar.preparedSelection(root, shape());

        assertNull(finite.getFieldConfig("replaces").inheritedFrom());
        assertTrue(finite.getFieldConfig("replaces").hasField("country"));
        assertSame(finite, ViewConfigDesugar.preparedView(finite, shape()));
    }

    @Test void anObjectAlreadyOnTheValuePathIsStillABackReference() {
        king.replaces.add(president);
        ViewConfig root = ticks("name");
        ViewConfig first = ticks("name");
        ViewConfig second = ticks("name");
        ViewConfig third = ticks("name");
        second.addField("replaces", third);
        first.addField("replaces", second);
        root.addField("replaces", first);
        MockDisclosure disclosure = new MockDisclosure()
                .expand(president.replaces).expand(emperor)
                .expand(emperor.replaces).expand(king)
                .expand(king.replaces);

        assertTrue(render(root, disclosure).contains(
                "BACKREF replaces[0].replaces[0].replaces[0] \"President\""));
    }

    private String render(ViewConfig config, Disclosure disclosure) {
        ViewConfig literal = ViewConfigDesugar.literal(config, shape());
        MockSink sink = new MockSink();
        new RenderExecutor(new PlanResolver(), value -> null, value -> false, disclosure)
                .render(president, literal, sink);
        return sink.traceText();
    }
}
