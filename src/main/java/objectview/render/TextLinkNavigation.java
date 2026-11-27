package objectview.render;

import objectview.Viewable;
import objectview.field.LinkedTextSequence.Part;
import objectview.utils.BrowserLauncher;

import java.util.function.Predicate;

/** Both painted text and its full-text reader follow the same kinds of destinations. */
final class TextLinkNavigation {
    private TextLinkNavigation() { }

    static String destination(Part part) {
        if (part == null) return null;
        if (part.reference() == null) return part.url().isBlank() ? null : part.url();
        Viewable target = part.reference();
        String name = target.getDisplayName();
        return name == null || name.isBlank() ? target.getIdentifier() : name;
    }

    static boolean open(Part part, RenderContext context, Predicate<Viewable> reveal) {
        if (part == null) return false;
        if (part.reference() != null) {
            if (!reveal.test(part.reference())) ReferenceRow.openFullObject(part.reference(), context);
            return true;
        }
        if (part.url().isBlank()) return false;
        BrowserLauncher.open(part.url());
        return true;
    }
}
