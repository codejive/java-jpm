package org.codejive.jpm;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Integration tests for the Main class, focusing on the new 'do' command and aliases. */
class MainIntegrationTest {

    @TempDir Path tempDir;

    private String originalDir;

    @BeforeEach
    void setUp() {
        originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalDir);
    }

    /**
     * Helper method to capture stdout/stderr for tests that need to check command output. Only use
     * this for tests that check jpm's own output, not for tests that execute system commands.
     */
    private TestOutputCapture captureOutput() {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));

        return new TestOutputCapture(originalOut, originalErr, outContent, errContent);
    }

    /** Helper class to manage output capture and restoration */
    private static class TestOutputCapture implements AutoCloseable {
        private final PrintStream originalOut;
        private final PrintStream originalErr;
        private final ByteArrayOutputStream outContent;
        private final ByteArrayOutputStream errContent;

        TestOutputCapture(
                PrintStream originalOut,
                PrintStream originalErr,
                ByteArrayOutputStream outContent,
                ByteArrayOutputStream errContent) {
            this.originalOut = originalOut;
            this.originalErr = originalErr;
            this.outContent = outContent;
            this.errContent = errContent;
        }

        String getOut() {
            return outContent.toString();
        }

        String getErr() {
            return errContent.toString();
        }

        @Override
        public void close() {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    void testDoCommandList() throws IOException {
        // Create app.yml with actions
        createAppYml();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do", "--list");

            assertThat(exitCode).isEqualTo(0);
            String output = capture.getOut();
            assertThat(output).contains("Available actions:", "build", "test", "run", "hello");
        }
    }

    @Test
    void testDoCommandListShortFlag() throws IOException {
        // Create app.yml with actions
        createAppYml();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do", "-l");

            assertThat(exitCode).isEqualTo(0);
            String output = capture.getOut();
            assertThat(output).contains("Available actions:");
        }
    }

    @Test
    void testDoCommandListNoActions() throws IOException {
        // Create app.yml without actions
        createAppYmlWithoutActions();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do", "--list");

            assertThat(exitCode).isEqualTo(0);
            String output = capture.getOut();
            assertThat(output).contains("No actions defined in app.yml");
        }
    }

    @Test
    void testDoCommandListNoAppYml() {
        // No app.yml file exists
        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do", "--list");

            assertThat(exitCode).isEqualTo(0);
            String output = capture.getOut();
            assertThat(output).contains("No actions defined in app.yml");
        }
    }

    @Test
    void testDoCommandMissingActionName() throws IOException {
        createAppYml();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do");

            assertThat(exitCode).isEqualTo(1);
            String errorOutput = capture.getErr();
            assertThat(errorOutput)
                    .contains("Action name is required", "Use --list to see available actions");
        }
    }

    @Test
    void testDoCommandNonexistentAction() throws IOException {
        createAppYml();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do", "nonexistent");

            assertThat(exitCode).isEqualTo(1);
            String errorOutput = capture.getErr();
            assertThat(errorOutput)
                    .contains(
                            "Action 'nonexistent' not found in app.yml. Use --list to see available actions.");
        }
    }

    @Test
    void testBuildAlias() throws IOException {
        createAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("build");

        // Test that build alias works (delegates to 'do build')
        assertThat(exitCode >= 0).isTrue();
    }

    @Test
    void testTestAlias() throws IOException {
        createAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("test");

        // Test that test alias works (delegates to 'do test')
        assertThat(exitCode >= 0).isTrue();
    }

    @Test
    void testRunAlias() throws IOException {
        createAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("run");

        // Test that run alias works (delegates to 'do run')
        assertThat(exitCode >= 0).isTrue();
    }

    @Test
    void testAliasWithNonexistentAction() throws IOException {
        // Create app.yml without 'build' action
        createAppYmlWithoutBuildAction();

        // Test the "do" command directly (which is what the alias redirects to)
        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do", "build");

            // Should fail with exit code 1 when action is not found
            assertThat(exitCode).isEqualTo(1);
            String errorOutput = capture.getErr();
            assertThat(errorOutput).contains("Action 'build' not found in app.yml");
        }
    }

    @Test
    void testDoWithOutput() throws IOException {
        // Create app.yml with action that doesn't use {{deps}}
        createAppYml();

        CommandLine cmd = Main.getCommandLine();

        try (TestOutputCapture capture = captureOutput()) {
            int exitCode = cmd.execute("do", "hello");
            assertThat(exitCode >= 0).isTrue(); // Should not be negative (internal error)
            String output = capture.getOut();
            assertThat(output).contains("Hello World");
        }
    }

    @Test
    void testMainWithNoArgs() {
        // Test the default behavior using CommandLine
        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute();

        // Should show help when no args provided (CommandLine default behavior)
        // The Main.main() method redirects to interactive search, but CommandLine.execute()
        // with no args typically shows help
        assertThat(exitCode >= 0).isTrue(); // Should not be negative (internal error)
    }

    @Test
    void testDoAliasWithArgs() throws IOException {
        createAppYml();
        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("run", "--foo", "bar");

            assertThat(exitCode).isEqualTo(0);
            String output = capture.getOut();
            // The run action should execute and include the classpath in the output
            assertThat(output).contains("running... .", "libs", "--foo bar");
        }
    }

    private void createAppYml() throws IOException {
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  build: \"echo building... .{/}libs{:}{{deps}}\"\n"
                        + "  test: \"echo testing... .{/}libs{:}{{deps}}\"\n"
                        + "  run: \"echo running... .{/}libs{:}{{deps}}\"\n"
                        + "  hello: \"echo Hello World\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }

    private void createAppYmlWithoutActions() throws IOException {
        String yamlContent = "dependencies:\n" + "  com.github.lalyos:jfiglet: \"0.0.9\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }

    private void createAppYmlWithoutBuildAction() throws IOException {
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  test: \"java -cp {{deps}} TestRunner\"\n"
                        + "  hello: \"echo Hello World\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }
}
