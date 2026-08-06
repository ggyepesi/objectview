package objectview.demo;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Hidden;
import objectview.search.SearchPanel;
import objectview.table.SearchableTableView;
import objectview.viewconfig.ViewConfig;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runnable examples of the table projection, including nested references,
 * nested collections, null nested values and subtype-only fields.
 */
public final class TableViewDemo {
    private TableViewDemo() {}

    /** A nested reference rendered as one display column when it is not expanded. */
    private static final class Category extends ViewableAdapter {
        @DisplayField
        private final String name;

        private Category(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    /** A nested reference whose selected fields become discoverer.* columns. */
    private static final class Scientist extends ViewableAdapter {
        @DisplayField
        private final String name;
        private final String country;

        private Scientist(String name, String country) {
            this.name = name;
            this.country = country;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    /** Elements of a nested collection; their fields become occurrences.* columns. */
    private static final class Occurrence extends ViewableAdapter {
        @DisplayField
        private final String environment;
        private final String abundance;

        private Occurrence(String environment, String abundance) {
            this.environment = environment;
            this.abundance = abundance;
        }

        @Override public String getIdentifier() { return environment; }
        @Override public String getDisplayName() { return environment; }
    }

    private static class Element extends ViewableAdapter {
        @DisplayField
        private final String name;
        private final int atomicNumber;
        private final Category category;
        private final Scientist discoverer; // deliberately null for an ancient element
        private final List<Occurrence> occurrences;
        private final List<String> phases;
        private final Map<String, String> facts;

        private Element(String name, int atomicNumber, Category category,
                        Scientist discoverer, List<Occurrence> occurrences,
                        List<String> phases, Map<String, String> facts) {
            this.name = name;
            this.atomicNumber = atomicNumber;
            this.category = category;
            this.discoverer = discoverer;
            this.occurrences = occurrences;
            this.phases = phases;
            this.facts = facts;
        }

        @Override public String getIdentifier() { return Integer.toString(atomicNumber); }
        @Override public String getDisplayName() { return name; }
    }

    /** Its additional field becomes a column with empty cells for base rows. */
    private static final class RadioactiveElement extends Element {
        private final String halfLife;

        private RadioactiveElement(
                String name, int atomicNumber, Category category,
                Scientist discoverer, List<Occurrence> occurrences,
                List<String> phases, Map<String, String> facts,
                String halfLife) {
            super(name, atomicNumber, category, discoverer, occurrences, phases, facts);
            this.halfLife = halfLife;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TableViewDemo::show);
    }

    private static void show() {
        Category nonmetal = new Category("Nonmetal");
        Category postTransition = new Category("Post-transition metal");
        Scientist cavendish = new Scientist("Henry Cavendish", "Great Britain");
        Scientist curie = new Scientist("Marie Curie", "Poland / France");

        Element hydrogen = new Element(
                "Hydrogen", 1, nonmetal, cavendish,
                List.of(
                        new Occurrence("Stars", "very abundant"),
                        new Occurrence("Earth's water", "compound")),
                List.of("gas", "liquid"),
                facts("symbol", "H", "group", "1"));
        Element carbon = new Element(
                "Carbon", 6, nonmetal, null,
                List.of(
                        new Occurrence("Living organisms", "essential"),
                        new Occurrence("Earth's crust", "0.02%")),
                List.of("solid", "liquid"),
                facts("symbol", "C", "group", "14"));
        RadioactiveElement polonium = new RadioactiveElement(
                "Polonium", 84, postTransition, curie,
                List.of(
                        new Occurrence("Uranium ores", "trace"),
                        new Occurrence("Synthetic production", "available")),
                List.of("solid", "liquid"),
                facts("symbol", "Po", "group", "16"),
                "138 days (Po-210)");
        List<Element> elements = List.of(hydrogen, carbon, polonium);

        SearchableTableView view = SearchableTableView.builder(elements)
                .sample(hydrogen)
                .configState(new SearchPanel.ConfigState(
                        null, null, initialViewConfig()))
                .subtypeConfigs(List.of(new SearchPanel.SubtypeConfig(
                        "RadioactiveElement", "Element", polonium, null,
                        Set.of("halfLife"), value -> value instanceof RadioactiveElement)))
                .build();

        JLabel explanation = new JLabel(
                "<html><b>Nested examples:</b> category is collapsed; "
                        + "discoverer.* is expanded; occurrences.* fans out through a "
                        + "collection; Carbon has a null discoverer; halfLife belongs only "
                        + "to RadioactiveElement. Use ▲/▼ in collection and map cells.</html>");
        explanation.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JFrame frame = new JFrame("ObjectView table — nested fields");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(4, 4));
        frame.add(explanation, BorderLayout.NORTH);
        frame.add(view, BorderLayout.CENTER);
        frame.setSize(1450, 480);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * This is ordinary ViewConfig—the table introduces no separate column model.
     * A leaf config keeps a reference collapsed; a child config projects the
     * selected nested fields into dotted columns.
     */
    private static ViewConfig initialViewConfig() {
        ViewConfig scientist = ViewConfig.of(Scientist.class);
        scientist.setAllFields(false);
        scientist.addField("name", ViewConfig.leaf());
        scientist.addField("country", ViewConfig.leaf());

        ViewConfig occurrence = ViewConfig.of(Occurrence.class);
        occurrence.setAllFields(false);
        occurrence.addField("environment", ViewConfig.leaf());
        occurrence.addField("abundance", ViewConfig.leaf());

        ViewConfig element = ViewConfig.of(Element.class);
        element.setAllFields(false);
        element.addField("name", ViewConfig.leaf());
        element.addField("atomicNumber", ViewConfig.leaf());
        element.addField("category", ViewConfig.leaf());
        element.addField("discoverer", scientist);
        element.addField("occurrences", occurrence);
        element.addField("phases", ViewConfig.leaf());
        element.addField("facts", ViewConfig.leaf());
        return element;
    }

    private static Map<String, String> facts(String... entries) {
        Map<String, String> facts = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            facts.put(entries[i], entries[i + 1]);
        }
        return facts;
    }
}
