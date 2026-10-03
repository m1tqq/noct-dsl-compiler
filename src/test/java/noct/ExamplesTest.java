package noct;

import noct.diagnostic.NoctError;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static noct.TestSupport.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every file in {@code examples/} must pass all checks, and every file in
 * {@code examples/errors/} must fail with the message given on its first
 * line, written as {@code # expect: <message>}.
 */
class ExamplesTest {

    private static final String EXPECT = "# expect: ";

    private static List<Path> noctFiles(String directory) throws IOException {
        try (Stream<Path> files = Files.list(Path.of(directory))) {
            return files.filter(p -> p.toString().endsWith(".noct")).sorted().toList();
        }
    }

    @TestFactory
    Stream<DynamicTest> examplesPassAllChecks() throws IOException {
        List<Path> files = noctFiles("examples");
        assertFalse(files.isEmpty());
        return files.stream().map(file -> DynamicTest.dynamicTest(file.toString(),
                () -> Noct.check(Files.readString(file))));
    }

    @TestFactory
    Stream<DynamicTest> errorExamplesFailWithExpectedMessage() throws IOException {
        List<Path> files = noctFiles("examples/errors");
        assertFalse(files.isEmpty());
        return files.stream().map(file -> DynamicTest.dynamicTest(file.toString(), () -> {
            String source = Files.readString(file);
            assertTrue(source.startsWith(EXPECT), file + " must start with '" + EXPECT + "<message>'");
            String expected = source.lines().findFirst().orElseThrow().substring(EXPECT.length());

            NoctError error = assertError(() -> Noct.check(source));
            assertEquals(expected, error.getMessage());
        }));
    }
}
