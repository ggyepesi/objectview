package objectview.demo;

import objectview.Viewable;
import objectview.demo.BenchmarkFixture.Item;
import objectview.render.CardListView;
import objectview.render.RenderContext;
import objectview.search.SearchPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Stress demo for objectview's rendering stack: builds a view over
 * <em>tens of thousands</em> of {@link Viewable}s and shows that it stays
 * responsive because {@link objectview.virtual.VirtualizedCardList} only
 * materializes the cards actually on screen.
 *
 * <p>Run with an optional count (default 100,000) and materialize sample:
 * <pre>
 *   java objectview.demo.RenderBenchmark 100000 1000
 * </pre>
 *
 * <p>It times and reports, for that many rich cards (each with several text
 * fields, a reference chip, and a collection of reference chips):
 * <ul>
 *   <li><b>generate</b> — build the object graph (off the EDT),</li>
 *   <li><b>register</b> — pre-register every object in the shared context so
 *       references resolve to navigable chips,</li>
 *   <li><b>build</b> — wire the virtualized card list without materializing it,</li>
 *   <li><b>first visible</b> — show, naturally materialize the viewport and
 *       synchronously paint it,</li>
 *   <li><b>navigate</b> — jump to and flash the very last card (forces a
 *       build near the end — the O(1) virtualized jump),</li>
 *   <li><b>search</b> — filter by a substring across all objects,</li>
 *   <li><b>render</b> — materialize a fixed sample of cards and report their
 *       retained heap, the figure comparable with {@link TableRenderBenchmark}.</li>
 * </ul>
 * The window is then interactive: scroll the whole range, click a chip to
 * navigate, or type in the search box.
 *
 * @see TableRenderBenchmark the same objects laid out as columns
 */
public class RenderBenchmark {

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int sample = args.length > 1
                ? Integer.parseInt(args[1])
                : BenchmarkFixture.DEFAULT_MATERIALIZE_SAMPLE;

        long g0 = System.nanoTime();
        List<Item> items = BenchmarkFixture.generate(n);
        long genMs = ms(g0);

        SwingUtilities.invokeLater(() -> launch(items, genMs, sample));
    }

    private static void launch(List<Item> items, long genMs, int sample) {
        int n = items.size();

        RenderContext ctx = new RenderContext();
        ctx.setInPlaceNavigation(true);

        long dataHeap = BenchmarkFixture.settledHeapBytes();

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

        JFrame frame = new JFrame("objectview — RenderBenchmark (cards)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setSize(900, 800);
        frame.setLocationRelativeTo(null);
        long visibleStart = System.nanoTime();
        frame.setVisible(true);
        // Complete the initial on-screen paint before stopping the clock. This
        // keeps materialization in its real path: viewport layout -> lazy build
        // -> layout -> paint, rather than calling buildIfNeeded from the benchmark.
        frame.getRootPane().paintImmediately(frame.getRootPane().getBounds());
        Toolkit.getDefaultToolkit().sync();
        long visibleMs = ms(visibleStart);
        int initiallyMaterialized =
                BenchmarkFixture.materializedCount(view.getVirtualList());

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
                engine.runCoordinatedSearch(BenchmarkFixture.NEEDLE);
                searchMs = ms(t);
            } catch (RuntimeException ignore) { /* best-effort timing */ }

            // Clear the timed search so the sample renders — and the user starts —
            // on the full set.
            engine.runCoordinatedSearch("");

            BenchmarkFixture.Materialization rendered =
                    BenchmarkFixture.measureMaterialization(items, sample,
                            q -> view.getVirtualList().buildIfNeeded(q));

            String report = String.format(
                    "CARDS  %,d items  —  generate %d ms · register %d ms · build %d ms"
                            + " · first-visible %d ms (%d materialized)"
                            + " · jump-to-last %d ms · search \"%s\" %d ms"
                            + "%n       data %s  ·  cards: %s",
                    n, genMs, regMs, buildMs, visibleMs, initiallyMaterialized,
                    navMs, BenchmarkFixture.NEEDLE, searchMs,
                    BenchmarkFixture.mb(dataHeap), rendered);
            header.setText("<html>" + report.replace("\n", "<br>") + "</html>");
            System.out.println(report);
            System.out.println(
                    "Interactive: scroll the full range, click a chip to "
                            + "navigate, or type in the search box.");
        });
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
