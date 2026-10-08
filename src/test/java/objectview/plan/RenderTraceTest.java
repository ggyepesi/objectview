package objectview.plan;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Reference;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rendering contract, observed without Swing: the executor's complete decision
 * trace for History's shape, and every value it read. A field is rendered if and only if
 * it is ticked; an unticked field is never read.
 */
class RenderTraceTest {

    private final HistoryShape h = new HistoryShape();

    /** The reported failure: only offices.position.Display ticked. */
    @Test void onlyTheTickedPositionCaptionsShowAndSourceIsNeverRead() {
        ViewConfig config = person(offices(field("position", ticks("@view:display"))));

        assertTrace(render(config, opened(), notTopLevel()), """
                OBJECT <root> caption="Wigmund of Mercia" open
                  COLLECTION offices (2) open
                    OBJECT offices[0] open
                      OBJECT offices[0].position caption="King of Mercia" open
                        SKIP offices[0].position.country OFF
                        SKIP offices[0].position.superClasses OFF
                      SKIP offices[0].label OFF
                      SKIP offices[0].source OFF
                      SKIP offices[0].startDate OFF
                      SKIP offices[0].endDate OFF
                    OBJECT offices[1] open
                      OBJECT offices[1].position caption="monarch" open
                        SKIP offices[1].position.country OFF
                        SKIP offices[1].position.superClasses OFF
                      SKIP offices[1].label OFF
                      SKIP offices[1].source OFF
                      SKIP offices[1].startDate OFF
                      SKIP offices[1].endDate OFF
                  SKIP epithet OFF""");
        assertTrue(h.reads.stream().noneMatch(read -> read.endsWith(".source")
                        || read.startsWith("OfficeHolding") && read.endsWith(".label")),
                "neither source nor the office's own label is read: " + h.reads);
    }

    /** Phase 0: the old default ticked offices.source without the user choosing it. */
    @Test void theDefaultShowsOfficesFoldedAndReadsNoMember() {
        ViewConfig defaults = ViewDefaults.newView(h.shape(h.wigmund));

        assertTrace(render(defaults, new MockDisclosure(), notTopLevel()), """
                OBJECT <root> caption="Wigmund of Mercia" open
                  COLLECTION offices (2) folded
                    DEFER offices COLLAPSED
                  SKIP epithet ABSENT""");
        // epithet has no entry, so it is answered without a read; no office is read.
        assertEquals(List.of("Person:Wigmund of Mercia.name",
                "Person:Wigmund of Mercia.offices"), h.reads);
    }

    /** The default ticks each office's DISPLAY, so an opened office reads as its label,
     *  and nothing else of the office is read. */
    @Test void theDefaultOpenedShowsEachOfficeByItsDisplayAlone() {
        ViewConfig defaults = ViewDefaults.newView(h.shape(h.wigmund));

        assertTrace(render(defaults, opened(), notTopLevel()), """
                OBJECT <root> caption="Wigmund of Mercia" open
                  COLLECTION offices (2) open
                    OBJECT offices[0] caption="Wigmund of Mercia" open
                      SKIP offices[0].source OFF
                      SKIP offices[0].position OFF
                      SKIP offices[0].startDate OFF
                      SKIP offices[0].endDate OFF
                    OBJECT offices[1] caption="Wigmund of Mercia" open
                      SKIP offices[1].source OFF
                      SKIP offices[1].position OFF
                      SKIP offices[1].startDate OFF
                      SKIP offices[1].endDate OFF
                  SKIP epithet ABSENT""");
        assertTrue(h.reads.stream().noneMatch(read -> read.endsWith(".source")
                        || read.endsWith(".position")),
                "only the office's DISPLAY is read: " + h.reads);
    }

    @Test void aTickedObjectWithNothingTickedUnderItIsNotRendered() {
        ViewConfig office = ticks("startDate");
        office.addField("position", ViewConfig.leaf());

        String trace = render(person(offices(office)), opened(), notTopLevel());

        assertTrue(trace.contains("SKIP offices[0].position OFF"),
                "a field-name caption alone is not a View state:\n" + trace);
        assertTrue(trace.contains("TEXT offices[0].startDate"), trace);
    }

    @Test void displayChangesOnlyTheCaption() {
        String without = render(person(offices(ticks("startDate"))), opened(), notTopLevel());
        String with = render(person(offices(ticks("label", "startDate"))), opened(), notTopLevel());

        assertEquals(without
                        .replace("OBJECT offices[0] open", "OBJECT offices[0] caption=\"Wigmund of Mercia\" open")
                        .replace("OBJECT offices[1] open", "OBJECT offices[1] caption=\"Wigmund of Mercia\" open")
                        .replace("      SKIP offices[0].label OFF\n", "")
                        .replace("      SKIP offices[1].label OFF\n", ""),
                with);
    }

    @Test void aScalarReferenceToACardIsALinkCaptionedOnlyByItsTickedDisplay() {
        Predicate<Viewable> kingshipHasACard = value -> value == h.kingOfMercia;

        String plain = render(person(offices(field("position", ticks("country")))),
                opened(), kingshipHasACard);
        String captioned = render(person(offices(field("position", ticks("name")))),
                opened(), kingshipHasACard);

        assertTrue(plain.contains("LINK-TO offices[0].position \"Open\""), plain);
        assertTrue(captioned.contains("LINK-TO offices[0].position \"King of Mercia\""),
                captioned);
    }

    /** The reported shape (#368): offices → position with its Display and its
     *  superClasses ticked. superClasses is a list of Position under a Position, so its
     *  members render by the position's config; they used to follow their own empty
     *  ticks, and the opened list showed nothing. */
    @Test void aListOfTheTypeAboveItOpensToItsMembersByThatTypesConfig() {
        ViewConfig position = ticks("@view:display");
        position.addField("superClasses", ViewConfig.leaf());
        ViewConfig config = person(offices(field("position", position)));
        MockDisclosure disclosure = opened()
                .expand(h.kingOfMercia.dynamicFieldValues().get("superClasses"));

        String trace = render(config, disclosure, notTopLevel());

        assertTrue(trace.contains(String.join("\n",
                "        COLLECTION offices[0].position.superClasses (1) open",
                "          OBJECT offices[0].position.superClasses[0] caption=\"monarch\" open",
                "            COLLECTION offices[0].position.superClasses[0].superClasses (0) folded")),
                trace);
    }

    @Test void anObjectAlreadyOnThePathIsABackReference() {
        String trace = render(person(offices(field("source", ticks("name")))),
                opened(), notTopLevel());

        assertTrue(trace.contains("BACKREF offices[0].source \"Wigmund of Mercia\""), trace);
    }

    @Test void anEmptyCollectionShowsZeroAndAnAbsentValueNothing() {
        h.wigmund.put("offices", new ArrayList<>());

        assertTrace(render(person(offices(ticks("label"))), opened(), notTopLevel()), """
                OBJECT <root> caption="Wigmund of Mercia" open
                  COLLECTION offices (0) folded
                  SKIP epithet OFF""");
    }

    @Test void foldingAndReopeningReRunsTheSamePlan() {
        ViewConfig config = person(offices(field("position", ticks("@view:display"))));
        Object offices = h.wigmund.dynamicFieldValues().get("offices");
        MockDisclosure disclosure = new MockDisclosure().expand(offices);
        PlanResolver resolver = new PlanResolver();

        String open = render(config, disclosure, notTopLevel(), resolver);
        disclosure.collapse(offices);
        String folded = render(config, disclosure, notTopLevel(), resolver);
        disclosure.expand(offices);
        String reopened = render(config, disclosure, notTopLevel(), resolver);

        assertTrue(folded.contains("DEFER offices COLLAPSED"), folded);
        assertEquals(open, reopened);
    }

    @Test void logicalTypesSharingOneCarrierClassGetTheirOwnPlans() {
        PlanResolver resolver = new PlanResolver();
        ViewConfig config = ticks("name");
        ObjectPlan wigmund = resolver.resolve(config,
                objectview.field.FieldSet.of(h.wigmund, HistoryShape.schema(h.wigmund)),
                "Person", true);
        ObjectPlan position = resolver.resolve(config,
                objectview.field.FieldSet.of(h.kingOfMercia, HistoryShape.schema(h.kingOfMercia)),
                "Position", true);

        assertSame(h.wigmund.getClass(), h.kingOfMercia.getClass());
        assertNotEquals(wigmund.describe(), position.describe());
        assertSame(position, resolver.resolve(config,
                objectview.field.FieldSet.of(h.monarch, HistoryShape.schema(h.monarch)),
                "Position", true), "one plan per (config, logical type)");
    }

    /** Every combination of ticks under offices: each value read is a ticked field. */
    @TestFactory Stream<DynamicTest> anUntickedFieldIsNeverRead() {
        List<String> officeFields = List.of("label", "source", "position", "startDate");
        List<String> positionFields = List.of("name", "country");
        List<DynamicTest> cases = new ArrayList<>();
        for (int officeMask = 0; officeMask < 16; officeMask++) {
            for (int positionMask = 0; positionMask < 4; positionMask++) {
                List<String> office = pick(officeFields, officeMask);
                List<String> position = pick(positionFields, positionMask);
                cases.add(DynamicTest.dynamicTest(office + " / position " + position, () -> {
                    HistoryShape fresh = new HistoryShape();
                    ViewConfig officeConfig = ViewConfig.leaf();
                    for (String name : office) {
                        officeConfig.addField(name,
                                name.equals("position") ? ticks(position.toArray(String[]::new))
                                        : ViewConfig.leaf());
                    }
                    ViewConfig root = ViewConfig.leaf();
                    root.addField("offices", officeConfig);
                    ViewConfig literal = ViewConfigDesugar.literal(root, fresh.shape(fresh.wigmund));
                    fresh.reads.clear();
                    new RenderExecutor(new PlanResolver(), HistoryShape::schema, v -> false,
                            new MockDisclosure().expand(fresh.wigmund.dynamicFieldValues().get("offices")))
                            .render(fresh.wigmund, literal, new MockSink());
                    Set<String> allowed = new java.util.HashSet<>(Set.of("Person.offices"));
                    office.forEach(name -> allowed.add("OfficeHolding." + name));
                    position.forEach(name -> allowed.add("Position." + name));
                    for (String read : fresh.reads) {
                        String type = read.substring(0, read.indexOf(':'));
                        String field = read.substring(read.lastIndexOf('.') + 1);
                        assertTrue(allowed.contains(type + "." + field),
                                "read an unticked field: " + read + " (ticked " + allowed + ")");
                    }
                }));
            }
        }
        return cases.stream();
    }

    /** The same classes declared in Java render the same trace. */
    @Test void reflectedAndDynamicBackingRenderTheSameTrace() {
        Ruler ruler = Ruler.wigmund();
        ViewConfig shorthandFree = person(offices(field("position", ticks("@view:display"))));
        ViewConfig literal = ViewConfigDesugar.literal(shorthandFree, TypeShape.ofClass(Ruler.class));
        MockSink sink = new MockSink();
        new RenderExecutor(new PlanResolver(), value -> null, v -> false,
                new MockDisclosure().expand(ruler.offices)).render(ruler, literal, sink);

        assertEquals(render(shorthandFree, opened(), notTopLevel()), sink.traceText());
    }

    // --- helpers ---

    private MockDisclosure opened() {
        return new MockDisclosure().expand(h.wigmund.dynamicFieldValues().get("offices"));
    }

    private static Predicate<Viewable> notTopLevel() { return value -> false; }

    private String render(ViewConfig config, Disclosure disclosure, Predicate<Viewable> topLevel) {
        return render(config, disclosure, topLevel, new PlanResolver());
    }

    private String render(ViewConfig config, Disclosure disclosure, Predicate<Viewable> topLevel,
                          PlanResolver resolver) {
        ViewConfig literal = ViewConfigDesugar.literal(config, h.shape(h.wigmund));
        h.reads.clear();
        MockSink sink = new MockSink();
        new RenderExecutor(resolver, HistoryShape::schema, topLevel, disclosure)
                .render(h.wigmund, literal, sink);
        return sink.traceText();
    }

    private static void assertTrace(String actual, String expected) {
        assertEquals(expected.stripIndent().strip(), actual);
    }

    private static ViewConfig person(ViewConfig offices) {
        ViewConfig config = ticks("@view:display");
        config.addField("offices", offices);
        return config;
    }

    private static ViewConfig offices(ViewConfig members) { return members; }

    private static ViewConfig field(String name, ViewConfig child) {
        ViewConfig config = ViewConfig.leaf();
        config.addField(name, child);
        return config;
    }

    private static ViewConfig ticks(String... names) {
        ViewConfig config = ViewConfig.leaf();
        for (String name : names) config.addField(name, ViewConfig.leaf());
        return config;
    }

    private static List<String> pick(List<String> names, int mask) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) if ((mask & (1 << i)) != 0) out.add(names.get(i));
        return out;
    }

    // --- the same shape, declared in Java ---

    static final class Ruler extends ViewableAdapter {
        @DisplayField private final String name;
        @Reference private final List<Office> offices = new ArrayList<>();
        private String epithet;

        Ruler(String name) { this.name = name; }

        static Ruler wigmund() {
            Ruler ruler = new Ruler("Wigmund of Mercia");
            Seat monarch = new Seat("monarch", null);
            Seat king = new Seat("King of Mercia", "Mercia");
            king.superClasses.add(monarch);
            ruler.offices.add(new Office("Wigmund of Mercia", ruler, king, "840"));
            ruler.offices.add(new Office("Wigmund of Mercia", ruler, monarch, "839"));
            return ruler;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return "getDisplayName(" + name + ")"; }
    }

    static final class Office extends ViewableAdapter {
        @DisplayField private final String label;
        @Reference private final Ruler source;
        @Reference private final Seat position;
        private final String startDate;
        private String endDate;

        Office(String label, Ruler source, Seat position, String startDate) {
            this.label = label;
            this.source = source;
            this.position = position;
            this.startDate = startDate;
        }

        @Override public String getIdentifier() { return label + startDate; }
        @Override public String getDisplayName() { return "getDisplayName(" + label + ")"; }
    }

    static final class Seat extends ViewableAdapter {
        @DisplayField private final String name;
        private final String country;
        @Reference private final List<Seat> superClasses = new ArrayList<>();

        Seat(String name, String country) {
            this.name = name;
            this.country = country;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return "getDisplayName(" + name + ")"; }
    }
}
