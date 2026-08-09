package objectview.demo;

import objectview.Viewable;
import objectview.demo.BenchmarkFixture.Item;
import objectview.field.ViewableFieldPaths;
import objectview.render.RenderContext;
import objectview.search.SearchPanel;
import objectview.table.ViewableColumnsView;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * {@link RenderBenchmark}'s twin for TABLE mode: the same synthetic objects, the
 * same steps and the same heap accounting, laid out as columns by
 * {@link ViewableColumnsView} instead of as cards. Run both to read the cost of
 * the LAYOUT alone — the data, the generator seed and the virtualization
 * underneath are identical.
 *
 * <pre>
 *   java objectview.demo.TableRenderBenchmark 100000 1000
 * </pre>
 *
 * <p>Two figures are worth watching next to the card run:
 * <ul>
 *   <li><b>columns</b> — the table projects columns from the declared/stable
 *       shape, so this step is independent of the item count and performs no
 *       member scan;</li>
 *   <li><b>first visible</b> — layout, natural viewport materialization and the
 *       first completed paint;</li>
 *   <li><b>rows</b> — retained bytes per materialized row against the card run's
 *       bytes per card. Both modes virtualize through the same
 *       {@link objectview.virtual.VirtualizedCardList}, so a screenful costs a
 *       screenful either way; this is what differs per rendered instance.</li>
 * </ul>
 */
public class TableRenderBenchmark {

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

        // The columns are projected from ONE stable sample, the same projection
        // SearchableView uses for TABLE mode, so every row offers the same paths.
        Viewable columnSample = items.get(0);

        long b0 = System.nanoTime();
        ViewableColumnsView table = new ViewableColumnsView(items, ctx,
                () -> ViewableFieldPaths.collectFromSample(
                        columnSample, objectview.viewconfig.ViewConfig.all(Item.class),
                        ViewableFieldPaths.ALL_FIELDS));
        long buildMs = ms(b0);

        SearchPanel engine = new SearchPanel(Item.class);
        engine.setRenderContext(ctx);
        engine.setCoordinated(true);

        // Applying the view config installs the config resolver, which is what lets
        // the table union its columns and raise the header — hence timed separately.
        long c0 = System.nanoTime();
        engine.setTargetAndApplyViewConfig(
                table, table.scrollPane(), table.scrollPane());
        long columnsMs = ms(c0);

        JLabel header = new JLabel();
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JPanel top = new JPanel(new BorderLayout(0, 2));
        top.add(header, BorderLayout.NORTH);
        top.add(engine, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(0, 4));
        root.add(top, BorderLayout.NORTH);
        root.add(table.scrollPane(), BorderLayout.CENTER);

        JFrame frame = new JFrame("objectview — TableRenderBenchmark (columns)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setSize(900, 800);
        frame.setLocationRelativeTo(null);
        long visibleStart = System.nanoTime();
        frame.setVisible(true);
        frame.getRootPane().paintImmediately(frame.getRootPane().getBounds());
        Toolkit.getDefaultToolkit().sync();
        long visibleMs = ms(visibleStart);
        int initiallyMaterialized = BenchmarkFixture.materializedCount(table);

        SwingUtilities.invokeLater(() -> {
            long navMs = -1, searchMs = -1;
            try {
                long t = System.nanoTime();
                table.navigateToTop(items.get(n - 1));   // jump to the last row
                navMs = ms(t);
            } catch (RuntimeException ignore) { /* best-effort timing */ }
            try {
                long t = System.nanoTime();
                engine.runCoordinatedSearch(BenchmarkFixture.NEEDLE);
                searchMs = ms(t);
            } catch (RuntimeException ignore) { /* best-effort timing */ }

            engine.runCoordinatedSearch("");

            BenchmarkFixture.Materialization rendered =
                    BenchmarkFixture.measureMaterialization(items, sample, table::row);

            String report = String.format(
                    "TABLE  %,d items  —  generate %d ms · register %d ms · build %d ms"
                            + " · columns(%d) %d ms · jump-to-last %d ms · search \"%s\" %d ms"
                            + "%n       first-visible %d ms (%d materialized)"
                            + "%n       data %s  ·  rows: %s",
                    n, genMs, regMs, buildMs, table.columns().size(), columnsMs, navMs,
                    BenchmarkFixture.NEEDLE, searchMs,
                    visibleMs, initiallyMaterialized,
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
