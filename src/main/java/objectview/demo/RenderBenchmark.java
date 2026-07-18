package objectview.demo;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.render.CardListView;
import objectview.render.RenderContext;
import objectview.search.SearchPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stress demo for objectview's rendering stack: builds a view over
 * <em>tens of thousands</em> of {@link Viewable}s and shows that it stays
 * responsive because {@link objectview.virtual.VirtualizedCardList} only
 * materializes the cards actually on screen.
 *
 * <p>Run with an optional count (default 100,000):
 * <pre>
 *   java objectview.demo.RenderBenchmark 100000
 * </pre>
 *
 * <p>It times and reports, for that many rich cards (each with several text
 * fields, a reference chip, and a collection of reference chips):
 * <ul>
 *   <li><b>generate</b> — build the object graph (off the EDT),</li>
 *   <li><b>register</b> — pre-register every object in the shared context so
 *       references resolve to navigable chips,</li>
 *   <li><b>build</b> — wire the virtualized card list (no visible card is built
 *       until the viewport is realized),</li>
 *   <li><b>navigate</b> — jump to and flash the very last card (forces a
 *       build near the end — the O(1) virtualized jump),</li>
 *   <li><b>search</b> — filter by a substring across all objects.</li>
 * </ul>
 * The window is then interactive: scroll the whole range, click a chip to
 * navigate, or type in the search box.
 */
public class RenderBenchmark {

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
    private static final String NEEDLE = "resonant";

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;

        long g0 = System.nanoTime();
        List<Item> items = generate(n);
        long genMs = ms(g0);

        SwingUtilities.invokeLater(() -> launch(items, genMs));
    }

    private static List<Item> generate(int n) {
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

    private static void launch(List<Item> items, long genMs) {
        int n = items.size();

        RenderContext ctx = new RenderContext();
        ctx.setInPlaceNavigation(true);

        long r0 = System.nanoTime();
        ctx.addTopLevels(items);                   // register for chip resolution
        long regMs = ms(r0);

        CardListView view = new CardListView();
        view.setRenderContext(ctx);

        long b0 = System.nanoTime();
        for (Item it : items) {
            view.addViewable(it);
        }
        view.createCardsPanel(1);                  // single tall virtualized column
        long buildMs = ms(b0);

        SearchPanel engine = new SearchPanel(Item.class);
        engine.setTarget(view.getCardsPanel(), view.getCardsScrollPane());
        engine.setRenderContext(ctx);
        engine.setCoordinated(true);
        view.addTargetListener(engine);

        JLabel header = new JLabel();
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel top = new JPanel(new BorderLayout(0, 2));
        top.add(header, BorderLayout.NORTH);
        top.add(engine, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(0, 4));
        root.add(top, BorderLayout.NORTH);
        root.add(view.getCardsScrollPane(), BorderLayout.CENTER);

        JFrame frame = new JFrame("objectview — RenderBenchmark");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setSize(900, 800);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Once the viewport is realized (so virtualization has a size to build
        // against), time an O(1) jump to the far end and a full-range search.
        SwingUtilities.invokeLater(() -> {
            long navMs = -1, searchMs = -1;
            try {
                long t = System.nanoTime();
                ctx.focusTopLevel(items.get(n - 1));   // jump to last card
                navMs = ms(t);
            } catch (RuntimeException ignore) { /* best-effort timing */ }
            try {
                long t = System.nanoTime();
                engine.runCoordinatedSearch(NEEDLE);   // filter across all N
                searchMs = ms(t);
            } catch (RuntimeException ignore) { /* best-effort timing */ }

            long usedMb = (Runtime.getRuntime().totalMemory()
                    - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

            String report = String.format(
                    "%,d cards  —  generate %d ms · register %d ms · build %d ms"
                            + " · jump-to-last %d ms · search \"%s\" %d ms  ·  heap ~%d MB",
                    n, genMs, regMs, buildMs, navMs, NEEDLE, searchMs, usedMb);
            header.setText(report);
            System.out.println(report);
            System.out.println(
                    "Interactive: scroll the full range, click a chip to "
                            + "navigate, or type in the search box.");
            // Clear the timed search so the user starts on the full set.
            engine.runCoordinatedSearch("");
        });
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
