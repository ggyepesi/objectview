# objectview

A Swing library for **rendering, searching, and virtualizing large collections of
structured objects** — through one small SPI. Implement `Viewable` on your own
type and objectview gives you painted cards, live search/sort, reference-chip
navigation, and a virtualized list that stays smooth at **tens of thousands** of
objects.

It carries almost no baggage: its entire dependency footprint is `slf4j-api`
(logging facade — you supply the binding) and `jackson-databind` (view-config
JSON). Images are rendered by the JDK; SVG and image-blurring are delegated to
optional host-registered SPIs, so there is no heavy image-toolkit dependency.

## Quickstart

Make any class renderable by implementing `Viewable` (here via the reflection
base `ViewableAdapter`, so public fields become card rows):

```java
public final class Book extends ViewableAdapter {
    public String title;
    public String author;
    public int year;
    public Book sequelTo;                 // a reference — renders as a clickable chip

    @Override public String getIdentifier()  { return title; }
    @Override public String getDisplayName() { return title; }
}

MultiView view = new MultiView();
view.addSection("Books", Book.class, List.of(a, b));
view.build(1);                            // drop `view` (a JComponent) into any JFrame
```

Reference fields (`sequelTo`, or a `List<Book>`) render as chips that scroll to
and flash the target's card. See `objectview.demo.Quickstart` for the full runnable
version.

## What you get

- **Cards** — each object painted as a card of typed field rows (text, numbers,
  links, images, references), with selectable/copyable text.
- **Search & sort** — a live search bar over the current set, with per-field
  configuration (`ViewConfig`) and match highlighting.
- **Virtualization** — `VirtualizedCardList` builds only the cards on screen, so
  100k+ objects load in under a second and jumps/searches stay instant.
- **Reference navigation** — entity-valued fields become chips that navigate to
  the referent's card across views sharing a `RenderContext`.
- **Grouping/faceting** — build group trees from facet declarations
  (`objectview.facet`, `objectview.group`).

## The SPI

- **`objectview.Viewable`** — the one interface a host object implements:
  `getIdentifier()`, `getDisplayName()`, `typeName()`, and `fields()` (a `FieldSet`).
  `ViewableAdapter` provides a reflection-based default (public fields → rows);
  or supply a map-backed `FieldSet` for dynamic objects.
- **`objectview.utils.swing.SvgRasterizer`** — optional. Register a rasterizer to
  display SVG images; without one, raster formats still work.
- **`objectview.media.ImageBlurrer`** — optional. Register a policy to blur
  answer-revealing images (e.g. for quiz apps); default is no-op.

## Examples

Runnable demos in `objectview.demo`:

| demo | shows |
|------|-------|
| `Quickstart` | the 30-second example above |
| `SearchDemo` | live search, sort, field highlight + bulk expand/collapse toolbar |
| `MultiViewDemo` | two views sharing a context; click a chip to navigate between them |
| `RenderBenchmark [count]` | scale — 100,000 rich cards |

Benchmark numbers (100,000 cards, each with several fields + a reference chip +
a collection of chips) on a laptop:

```
generate 285 ms · register 54 ms · build 923 ms · jump-to-last 82 ms · search 52 ms · heap ~75 MB
```

## Build & use

Requires JDK 21.

```bash
mvn install          # build, test, install to your local Maven repo
```

```xml
<dependency>
    <groupId>io.github.ggyepesi</groupId>
    <artifactId>objectview</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

You also need an slf4j binding at runtime (objectview only depends on the API),
e.g. `org.slf4j:slf4j-simple`.

## License

[Apache License 2.0](LICENSE).

---

objectview began as the rendering layer of a larger Wikidata quiz-building
application and was extracted into a standalone, dependency-light library.
