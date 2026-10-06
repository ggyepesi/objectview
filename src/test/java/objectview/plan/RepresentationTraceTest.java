package objectview.plan;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Inline;
import objectview.annotations.Link;
import objectview.media.MediaValue;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each representation reaches its own mock counterpart, and the initial disclosure
 * follows the one policy: ordinary collections fold, a single image and {@code @Inline}
 * content start open. Disclosure never changes which fields are ticked.
 */
class RepresentationTraceTest {

    @Test void textLinkAndMediaAreLeavesOfTheirOwnKind() {
        assertEquals("""
                OBJECT <root> caption="Andromeda" open
                  TEXT area "722"
                  LINK chart "https://example.org/andromeda"
                  MEDIA flag "flag"
                  SKIP gallery OFF
                  SKIP steps OFF""", render(ticks("title", "area", "chart", "flag")));
    }

    @Test void aSingleImageAndInlineContentStartOpenWhileOtherCollectionsFold() {
        String trace = render(ticks("gallery", "steps"));

        assertEquals("""
                OBJECT <root> open
                  COLLECTION gallery (1) open
                    MEDIA gallery[0] "only"
                  COLLECTION steps (2) open
                    OBJECT steps[0] open
                      SKIP steps[0].text OFF
                      SKIP steps[0].@view:display OFF
                    OBJECT steps[1] open
                      SKIP steps[1].text OFF
                      SKIP steps[1].@view:display OFF
                  SKIP title OFF
                  SKIP area OFF
                  SKIP chart OFF
                  SKIP flag OFF""", trace);
    }

    @Test void aSecondImageFoldsTheGallery() {
        Constellation andromeda = new Constellation();
        andromeda.gallery = List.of(new Image("one"), new Image("two"));

        assertEquals("""
                OBJECT <root> open
                  COLLECTION gallery (2) folded
                    DEFER gallery COLLAPSED
                  SKIP title OFF
                  SKIP area OFF
                  SKIP chart OFF
                  SKIP flag OFF
                  SKIP steps OFF""", render(andromeda, ticks("gallery")));
    }

    private static String render(ViewConfig config) {
        return render(new Constellation(), config);
    }

    private static String render(Constellation value, ViewConfig config) {
        ViewConfig literal = ViewConfigDesugar.literal(
                config, TypeShape.ofClass(Constellation.class));
        MockSink sink = new MockSink();
        new RenderExecutor(new PlanResolver(), v -> null, v -> false, Disclosure.INITIAL)
                .render(value, literal, sink);
        return sink.traceText();
    }

    private static ViewConfig ticks(String... names) {
        ViewConfig config = ViewConfig.leaf();
        for (String name : names) config.addField(name, ViewConfig.leaf());
        return config;
    }

    static final class Constellation extends ViewableAdapter {
        @DisplayField private final String title = "Andromeda";
        private final Integer area = 722;
        @Link private final String chart = "https://example.org/andromeda";
        private final Image flag = new Image("flag");
        private List<Image> gallery = List.of(new Image("only"));
        @Inline private final List<Step> steps = List.of(new Step("a"), new Step("b"));

        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return "getDisplayName"; }
    }

    static final class Step extends ViewableAdapter {
        private final String text;
        Step(String text) { this.text = text; }
        @Override public String getIdentifier() { return text; }
        @Override public String getDisplayName() { return "getDisplayName"; }
    }

    record Image(String mediaLabel) implements MediaValue {
        @Override public String mediaUrl() { return "https://example.org/" + mediaLabel; }
        @Override public boolean mediaSvg() { return false; }
        @Override public String toString() { return mediaLabel; }
    }
}
