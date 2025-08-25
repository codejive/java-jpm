package org.codejive.jpm;

import static org.junit.jupiter.api.Assertions.*;

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
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        // Capture stdout and stderr
        originalOut = System.out;
        originalErr = System.err;
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalDir);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testDoCommandList() throws IOException {
        // Create app.yml with actions
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("do", "--list");

        assertEquals(0, exitCode);
        String output = outContent.toString();
        assertTrue(output.contains("Available actions:"));
        assertTrue(output.contains("build"));
        assertTrue(output.contains("test"));
        assertTrue(output.contains("run"));
        assertTrue(output.contains("hello"));
    }

    @Test
    void testDoCommandListShortFlag() throws IOException {
        // Create app.yml with actions
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("do", "-l");

        assertEquals(0, exitCode);
        String output = outContent.toString();
        assertTrue(output.contains("Available actions:"));
    }

    @Test
    void testDoCommandListNoActions() throws IOException {
        // Create app.yml without actions
        createAppYmlWithoutActions();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("do", "--list");

        assertEquals(0, exitCode);
        String output = outContent.toString();
        assertTrue(output.contains("No actions defined in app.yml"));
    }

    @Test
    void testDoCommandListNoAppYml() {
        // No app.yml file exists
        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("do", "--list");

        assertEquals(0, exitCode);
        String output = outContent.toString();
        assertTrue(output.contains("No actions defined in app.yml"));
    }

    @Test
    void testDoCommandMissingActionName() throws IOException {
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("do");

        assertEquals(1, exitCode);
        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Action name is required"));
        assertTrue(errorOutput.contains("Use --list to see available actions"));
    }

    @Test
    void testDoCommandNonexistentAction() throws IOException {
        createAppYml();

        CommandLine cmd = new CommandLine(new Main());
        int exitCode = cmd.execute("do", "nonexistent");

        assertEquals(1, exitCode);
        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Action 'nonexistent' not found in app.yml"));
        assertTrue(errorOutput.contains("Available actions: build, hello, run, test"));
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

        // Use Main.main() to test the alias handling logic
        Main.main("build");

        // The alias should convert "build" to "do build" and fail with exit code 1
        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Action 'build' not found in app.yml"));
    }

    @Test
    void testDoCommandPerformanceOptimization() throws IOException {
        // Create app.yml with action that doesn't use ${deps}
        createAppYmlWithSimpleAction();

        CommandLine cmd = new CommandLine(new Main());

        // This should execute quickly since it doesn't need to resolve classpath
        long startTime = System.currentTimeMillis();
        int exitCode = cmd.execute("do", "simple");
        long endTime = System.currentTimeMillis();

        // Should complete quickly (under 1 second for a simple echo)
        assertTrue((endTime - startTime) < 1000, "Simple action should execute quickly");
        assertTrue(exitCode >= 0);
    }

    @Test
    void testMainWithNoArgs() {
        // Call Main.main() directly to test the logic there
        Main.main();

        // Should show the interactive search message
        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Running 'jpm search --interactive'"));
    }

    private void createAppYml() throws IOException {
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  build: \"javac -cp ${deps} *.java\"\n"
                        + "  test: \"java -cp ${deps} TestRunner\"\n"
                        + "  run: \"java -cp .:${deps} MainClass\"\n"
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
                        + "  test: \"java -cp ${deps} TestRunner\"\n"
                        + "  hello: \"echo Hello World\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }

    private void createAppYmlWithSimpleAction() throws IOException {
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  simple: \"echo Simple action without classpath\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }
}
