package objectview.render;

/**
 * How a searchable instance view lays out its members — a pure PRESENTATION choice,
 * swapped at runtime over the same items and {@code ViewConfig} (search/sort/fields are
 * unaffected). Extensible: future modes (columnar row-per-instance, a periodic-table grid,
 * …) are added here.
 */
public enum RenderingMode {
    CARD("Cards"),
    TABLE("Table");

    private final String label;

    RenderingMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
