package objectview.demo;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.virtual.VirtualizedContainer;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * The synthetic domain and the measurement helpers shared by the rendering
 * benchmarks, so {@link RenderBenchmark} (cards) and {@link TableRenderBenchmark}
 * (columns) differ in NOTHING but the layout under test — same objects, same
 * generator seed, same heap accounting.
 */
public final class BenchmarkFixture {

    private BenchmarkFixture() {}

    /** A synthetic domain object with enough shape to exercise every row kind:
     *  text rows, a long text block, a numeric, a reference chip, and a
     *  collection of reference chips. */
    public static final class Item extends ViewableAdapter {
        public String name = "";
        public String headline = "";
        public String description = "";
        public String category = "";
        public int rank;
        public double score;
        public Item related;
        public List<Item> links = new ArrayList<>();

        public Item() {}

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
        @Override public String toString() { return name; }
    }

    private static final String[] ADJ = {
            "Radiant", "Silent", "Crimson", "Hollow", "Golden", "Northern",
            "Ancient", "Restless", "Velvet", "Distant", "Iron", "Amber"};
    private static final String[] NOUN = {
            "Nebula", "Harbor", "Circuit", "Meadow", "Signal", "Lantern",
            "Cascade", "Archive", "Beacon", "Foundry", "Meridian", "Quartz"};
    private static final int CATEGORIES = 40;

    /** A token seeded into ~1 in 8 descriptions, so the timed search filters a
     *  realistic fraction rather than everything or nothing. */
    public static final String NEEDLE = "resonant";

    /** How many items are materialized when measuring per-item render cost. */
    public static final int DEFAULT_MATERIALIZE_SAMPLE = 1_000;

    public static List<Item> generate(int n) {
        Random r = new Random(42);                 // deterministic run-to-run
        List<Item> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Item it = new Item();
            it.name = "item-" + i;
            it.headline = ADJ[r.nextInt(ADJ.length)] + " "
                    + NOUN[r.nextInt(NOUN.length)] + " #" + i;
            it.category = "Category-" + r.nextInt(CATEGORIES);
            it.rank = i;
            it.score = Math.round(r.nextDouble() * 10000) / 100.0;
            it.description = "A synthetic entry describing " + it.headline
                    + ", filed under " + it.category
                    + (i % 8 == 0 ? ", a " + NEEDLE + " outlier." : ".");
            items.add(it);
        }
        // Wire references to already-created items so the graph is acyclic and
        // every chip resolves to a real, navigable card.
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            if (i > 0) {
                it.related = items.get(r.nextInt(i));
            }
            int linkCount = r.nextInt(4);          // 0..3 collection chips
            for (int k = 0; k < linkCount && i > 0; k++) {
                it.links.add(items.get(r.nextInt(i)));
            }
        }
        return items;
    }

    /**
     * Live heap after asking for collection, so a before/after pair reads as
     * RETAINED bytes rather than allocation churn. {@code System.gc()} is a hint,
     * not a guarantee — treat the figures as indicative and compare runs, not
     * single numbers.
     */
    public static long settledHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        for (int pass = 0; pass < 3; pass++) {
            System.gc();
            try {
                Thread.sleep(60);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** What materializing {@code count} items costs. Component count is the
     *  deterministic structural measurement; retained heap remains indicative. */
    public record Materialization(int count, long bytes, long ms, long components) {
        public long bytesPerItem() {
            return count == 0 ? 0 : bytes / count;
        }

        public double componentsPerItem() {
            return count == 0 ? 0 : (double) components / count;
        }

        @Override public String toString() {
            return String.format(
                    "%,d rendered in %d ms · %s retained · ~%,d B/item"
                            + " · %.1f components/item",
                    count, ms, mb(bytes), bytesPerItem(), componentsPerItem());
        }
    }

    /**
     * Builds the first {@code sampleSize} items through {@code materialize} and
     * reports retained heap and component-tree size. Heap deltas are naturally
     * noisy because collection is nondeterministic; component count is stable
     * and makes wrapper-per-cell costs directly visible.
     */
    public static Materialization measureMaterialization(
            List<? extends Viewable> items,
            int sampleSize,
            Function<Viewable, JComponent> materialize) {

        int count = Math.max(0, Math.min(sampleSize, items.size()));
        long before = settledHeapBytes();
        long components = 0;

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            components += componentCount(materialize.apply(items.get(i)));
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        long after = settledHeapBytes();
        return new Materialization(
                count, Math.max(0, after - before), ms, components);
    }

    private static int componentCount(Component root) {
        if (root == null) return 0;
        int count = 1;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                count += componentCount(child);
            }
        }
        return count;
    }

    public static String mb(long bytes) {
        return String.format("%,d MB", bytes / (1024 * 1024));
    }

    /** Number of components naturally retained by the virtual viewport now. */
    public static int materializedCount(VirtualizedContainer container) {
        int[] count = {0};
        container.forEachMaterialized((item, component) -> count[0]++);
        return count[0];
    }
}
