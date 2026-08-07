package objectview.field;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericValuesTest {

    private static double val(Object v) {
        OptionalDouble n = NumericValues.parse(v);
        assertTrue(n.isPresent(), "expected a number for: " + v);
        return n.getAsDouble();
    }

    @Test void scalesMagnitudeWords() {
        assertEquals(300_000_000d, val("300 million"));
        assertEquals(1_538_000d, val("1538 K"));
        assertEquals(2_000_000_000d, val("2 billion"));
        assertEquals(22_209_690d, val("22.209690 million"), 0.5);
    }

    @Test void commaThousandsSeparators() {
        assertEquals(1_538_000d, val("1,538 K"));
        assertEquals(3885d, val("<3,885"));
    }

    @Test void rangeYieldsMidpoint() {
        assertEquals(10_500_000d, val("8–13 million"));   // (8+13)/2 = 10.5 million
        assertEquals(4d, val("3-5"));
    }

    @Test void plainAndTaggedNumbers() {
        assertEquals(5_000_000d, val("5 million (2020)"));   // first token wins
        assertEquals(210d, val("210"));
        assertEquals(3_000_000d, val("~3 million"));
    }

    @Test void numbersPassThroughUnscaled() {
        assertEquals(74.17d, val(74.17));
        assertEquals(90d, val(90));
    }

    @Test void percentIsNotOrderable() {
        assertEquals(OptionalDouble.empty(), NumericValues.parse("80% of China"));
    }

    @Test void proseAndBlankAreEmpty() {
        assertEquals(OptionalDouble.empty(), NumericValues.parse("L1 and L2 speakers in Scotland"));
        assertEquals(OptionalDouble.empty(), NumericValues.parse("  "));
        assertEquals(OptionalDouble.empty(), NumericValues.parse(null));
    }
}
