package noct;

import noct.diagnostic.Diagnostic;
import noct.diagnostic.NoctError;
import noct.diagnostic.Span;
import org.junit.jupiter.api.Test;

import static noct.TestSupport.assertError;
import static noct.TestSupport.lines;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosticTest {

    @Test
    void rendersSnippetWithCaretAndNotes() {
        String source = lines(
                "room cell \"Cell\":",
                "    door exit -> hal",
                "room hall \"Hall\":",
                "    door back -> cell");
        NoctError error = assertError(() -> Noct.check(source));

        assertEquals(lines(
                "error: unknown room 'hal'",
                " --> game.noct:2:18",
                "  |",
                "2 |     door exit -> hal",
                "  |                  ^^^",
                "  = help: did you mean 'hall'?"), Diagnostic.render(error, "game.noct", source));
    }

    @Test
    void gutterGrowsWithLineNumber() {
        NoctError error = NoctError.syntax(new Span(10, 1, 3), "oops");
        String source = "\n".repeat(9) + "abc\n";

        assertEquals(lines(
                "syntax error: oops",
                "  --> f.noct:10:1",
                "   |",
                "10 | abc",
                "   | ^^^"), Diagnostic.render(error, "f.noct", source));
    }

    @Test
    void errorsWithoutLocation() {
        NoctError error = NoctError.semantic(Span.NONE, "the program has no rooms").help("add one");
        assertEquals(lines(
                "error: the program has no rooms",
                " --> f.noct",
                "  = help: add one"), Diagnostic.render(error, "f.noct", ""));
    }
}
