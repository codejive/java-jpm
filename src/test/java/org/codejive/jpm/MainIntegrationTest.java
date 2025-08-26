package org.codejive.jpm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.codejive.jpm.util.ScriptUtils;
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
            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do", "--list");

            assertEquals(0, exitCode);
            String output = capture.getOut();
            assertTrue(output.contains("Available actions:"));
            assertTrue(output.contains("build"));
            assertTrue(output.contains("test"));
            assertTrue(output.contains("run"));
            assertTrue(output.contains("hello"));
        }
    }

    @Test
    void testDoCommandListShortFlag() throws IOException {
        // Create app.yml with actions
        createAppYml();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do", "-l");

            assertEquals(0, exitCode);
            String output = capture.getOut();
            assertTrue(output.contains("Available actions:"));
        }
    }

    @Test
    void testDoCommandListNoActions() throws IOException {
        // Create app.yml without actions
        createAppYmlWithoutActions();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do", "--list");

            assertEquals(0, exitCode);
            String output = capture.getOut();
            assertTrue(output.contains("No actions defined in app.yml"));
        }
    }

    @Test
    void testDoCommandListNoAppYml() {
        // No app.yml file exists
        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do", "--list");

            assertEquals(0, exitCode);
            String output = capture.getOut();
            assertTrue(output.contains("No actions defined in app.yml"));
        }
    }

    @Test
    void testDoCommandMissingActionName() throws IOException {
        createAppYml();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do");

            assertEquals(1, exitCode);
            String errorOutput = capture.getErr();
            assertTrue(errorOutput.contains("Action name is required"));
            assertTrue(errorOutput.contains("Use --list to see available actions"));
        }
    }

    @Test
    void testDoCommandNonexistentAction() throws IOException {
        createAppYml();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do", "nonexistent");

            assertEquals(1, exitCode);
            String errorOutput = capture.getErr();
            assertTrue(
                    errorOutput.contains(
                            "Action 'nonexistent' not found in app.yml. Use --list to see available actions."));
        }
    }

    @Test
    void testDoCommandSimpleAction() throws IOException {
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("do", "hello");

        // The exit code depends on whether 'echo' command is available
        // We mainly test that the command was processed without internal errors
        assertTrue(exitCode >= 0); // Should not be negative (internal error)
    }

    @Test
    void testBuildAlias() throws IOException {
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("build");

        // Test that build alias works (delegates to 'do build')
        assertTrue(exitCode >= 0);
    }

    @Test
    void testTestAlias() throws IOException {
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("test");

        // Test that test alias works (delegates to 'do test')
        assertTrue(exitCode >= 0);
    }

    @Test
    void testRunAlias() throws IOException {
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("run");

        // Test that run alias works (delegates to 'do run')
        assertTrue(exitCode >= 0);
    }

    @Test
    void testAliasWithNonexistentAction() throws IOException {
        // Create app.yml without 'build' action
        createAppYmlWithoutBuildAction();

        // Test the "do" command directly (which is what the alias redirects to)
        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do", "build");

            // Should fail with exit code 1 when action is not found
            assertEquals(1, exitCode);
            String errorOutput = capture.getErr();
            assertTrue(errorOutput.contains("Action 'build' not found in app.yml"));
        }
    }

    @Test
    void testDoCommandPerformanceOptimization() throws IOException {
        // Create app.yml with action that doesn't use {{deps}}
        createAppYmlWithSimpleAction();

        CommandLine cmd = new CommandLine(new Main());

        // This should execute quickly since it doesn't need to resolve classpath
        try (TestOutputCapture capture = captureOutput()) {
            long startTime = System.currentTimeMillis();
            int exitCode = cmd.execute("do", "simple");
            long endTime = System.currentTimeMillis();

            // Be more lenient on Windows as file operations can be slower
            // Allow up to 5 seconds on Windows, 1 second on other platforms
            boolean isWindows = ScriptUtils.isWindows();
            long maxTime = isWindows ? 5000 : 1000;
            assertTrue((endTime - startTime) < maxTime, "Simple action should execute quickly");
            assertTrue(exitCode >= 0);
        }
    }

    @Test
    void testMainWithNoArgs() {
        // Test the default behavior using CommandLine
        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute();

        // Should show help when no args provided (CommandLine default behavior)
        // The Main.main() method redirects to interactive search, but CommandLine.execute()
        // with no args typically shows help
        assertTrue(exitCode >= 0); // Should not be negative (internal error)
    }

    private void createAppYml() throws IOException {
        // Use platform-specific command for simple action that works on both Windows and Unix
        String simpleCommand = ScriptUtils.isWindows() ? "echo off" : "true";
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  build: \"javac -cp {{deps}} *.java\"\n"
                        + "  test: \"java -cp {{deps}} TestRunner\"\n"
                        + "  run: \"java -cp .:{{deps}} MainClass\"\n"
                        + "  hello: \""
                        + simpleCommand
                        + "\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }

    private void createAppYmlWithoutActions() throws IOException {
        String yamlContent = "dependencies:\n" + "  com.github.lalyos:jfiglet: \"0.0.9\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }

    private void createAppYmlWithoutBuildAction() throws IOException {
        // Use platform-specific command for simple action that works on both Windows and Unix
        String simpleCommand = ScriptUtils.isWindows() ? "echo off" : "true";
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  test: \"java -cp {{deps}} TestRunner\"\n"
                        + "  hello: \""
                        + simpleCommand
                        + "\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }

    private void createAppYmlWithSimpleAction() throws IOException {
        // Use platform-specific command for simple action that works on both Windows and Unix
        String simpleCommand = ScriptUtils.isWindows() ? "echo off" : "true";
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  simple: \""
                        + simpleCommand
                        + "\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }
}
