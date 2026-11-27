package objectview.render;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import java.awt.Dimension;
import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** References navigate by complete logical identity, not Java object identity or QID. */
class RenderContextIdentityNavigationTest {

    @Test void aReferenceShellFindsTheMatchingTypedTopLevelInstance() {
        Item person = new Item("Person", "Q1", "Person one");
        Item position = new Item("Position", "Q1", "Position one");
        Item personReference = new Item("Person", "Q1", "Person reference");
        Item otherTypeReference = new Item("Office", "Q1", "Office reference");
        RenderContext context = new RenderContext(List.of(person, position));
        AtomicReference<Object> resolved = new AtomicReference<>();
        AtomicInteger revealed = new AtomicInteger();
        JPanel host = new JPanel();
        JPanel card = new JPanel();
        host.add(card); // a detached component is deliberately treated as stale
        context.registerTopLevelRevealer(person, revealed::incrementAndGet);
        context.addTopLevelResolver(candidate -> {
            resolved.set(candidate);
            return candidate == person ? card : null;
        });

        assertTrue(context.isTopLevel(personReference));
        assertFalse(context.isTopLevel(otherTypeReference),
                "the same identifier in another logical type is not the same object");
        assertTrue(context.focusTopLevel(personReference));
        assertSame(person, resolved.get(),
                "the resolver receives the actual top-level instance, not the shell");
        assertEquals(1, revealed.get());
    }

    @Test void backRestoresTheOpenReaderAndFallsBackToTheStoryWhenItWasClosed() {
        EdtTests.onEdt(() -> {
            var story = new Item("Story", "1", "Odin at the well");
            var character = new Item("Character", "Q1", "Odin");
            var ctx = new RenderContext(List.of(story, character));
            var focusedStory = new AtomicInteger();
            var focusedCharacter = new AtomicInteger();
            var hosts = new JPanel();
            var characterCard = new JPanel();
            hosts.add(characterCard);
            ctx.registerTopLevelRevealer(character, focusedCharacter::incrementAndGet);
            ctx.registerTopLevelRevealer(story, focusedStory::incrementAndGet);
            ctx.addTopLevelResolver(target -> {
                if (target == character) return characterCard;
                // Resolve afresh; the text reader did not keep a story component alive.
                var rebuiltStory = new JPanel();
                hosts.add(rebuiltStory);
                return rebuiltStory;
            });

            var viewport = new JViewport();
            var text = new JPanel();
            viewport.setExtentSize(new Dimension(100, 100));
            viewport.setView(text);
            viewport.setViewSize(new Dimension(100, 900));
            var readerOpen = new AtomicBoolean(true);
            var raised = new AtomicInteger();
            Point original = new Point(0, 222);
            viewport.setViewPosition(original);
            assertTrue(ctx.focusFromReader(character, text, story, readerOpen::get, raised::incrementAndGet));
            assertEquals(1, focusedCharacter.get());
            viewport.setViewPosition(new Point(0, 440));
            assertTrue(ctx.back());
            assertEquals(original, viewport.getViewPosition());
            assertEquals(1, raised.get());
            assertEquals(0, focusedStory.get());
            assertFalse(ctx.back(), "the reader link pushed just one entry");

            assertTrue(ctx.focusFromReader(character, text, story, readerOpen::get, raised::incrementAndGet));
            readerOpen.set(false);
            ctx.updateBack();
            assertEquals("← Back to Odin at the well", ctx.backAction().getValue(Action.NAME));
            assertTrue(ctx.back());
            assertEquals(1, focusedStory.get());
            assertEquals(1, raised.get(), "Back must not reopen a closed reader");
            assertFalse(ctx.back(), "fallback must not push another entry");

            var unavailable = new RenderContext(List.of(character));
            unavailable.registerTopLevel(character, characterCard);
            assertTrue(unavailable.focusFromReader(character, text, story, readerOpen::get,
                    raised::incrementAndGet));
            assertFalse(unavailable.back(), "a closed reader and an unavailable story are skipped");

        });
    }

    private static final class Item extends ViewableAdapter {
        private final String type;
        private final String id;
        private final String label;

        private Item(String type, String id, String label) {
            this.type = type;
            this.id = id;
            this.label = label;
        }

        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return label; }
        @Override public String typeName() { return type; }
        @Override public String identityTypeName() { return type; }
    }
}
