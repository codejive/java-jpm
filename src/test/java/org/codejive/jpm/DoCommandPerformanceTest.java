package org.codejive.jpm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.codejive.jpm.util.ScriptUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import picocli.CommandLine;

/**
 * Test class specifically for verifying performance optimization in the 'do' command. This test
 * verifies that classpath resolution only happens when {{deps}} is present.
 */
class DoCommandPerformanceTest {

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
    void testPerformanceOptimizationNoDepsVariable() throws IOException {
        // Create app.yml with action that doesn't use {{deps}}
        createAppYmlWithSimpleAction();

        // Mock ScriptUtils to verify classpath is empty when no {{deps}} variable
        try (MockedStatic<ScriptUtils> mockedScriptUtils = Mockito.mockStatic(ScriptUtils.class)) {
            mockedScriptUtils
                    .when(() -> ScriptUtils.executeScript(anyString(), any()))
                    .thenReturn(0);

            CommandLine cmd = new CommandLine(new Main());
            int exitCode = cmd.execute("do", "simple");

            assertEquals(0, exitCode);

            // Verify that executeScript was called with empty classpath
            mockedScriptUtils.verify(
                    () ->
                            ScriptUtils.executeScript(
                                    eq("echo Simple action without classpath"),
                                    eq(Collections.emptyList())),
                    times(1));
        }
    }

    @Test
    void testCaseInsensitiveDepsVariable() throws IOException {
        // Test that only exact {{deps}} triggers optimization, not variations
        String yamlContent =
                "actions:\n"
                        + "  exact: \"java -cp {{deps}} MainClass\"\n"
                        + "  different: \"java -cp ${DEPS} MainClass\"\n"
                        + "  substring: \"java -cp mydeps MainClass\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);

        try (MockedStatic<ScriptUtils> mockedScriptUtils = Mockito.mockStatic(ScriptUtils.class)) {
            mockedScriptUtils
                    .when(() -> ScriptUtils.executeScript(anyString(), any()))
                    .thenReturn(0);

            CommandLine cmd = new CommandLine(new Main());

            // Test exact match - should resolve classpath
            cmd.execute("do", "exact");
            mockedScriptUtils.verify(
                    () -> ScriptUtils.executeScript(contains("{{deps}}"), any()), times(1));

            mockedScriptUtils.clearInvocations();

            // Test different case - should NOT resolve classpath
            cmd.execute("do", "different");
            mockedScriptUtils.verify(
                    () ->
                            ScriptUtils.executeScript(
                                    eq("java -cp ${DEPS} MainClass"), eq(Collections.emptyList())),
                    times(1));

            mockedScriptUtils.clearInvocations();

            // Test substring - should NOT resolve classpath
            cmd.execute("do", "substring");
            mockedScriptUtils.verify(
                    () ->
                            ScriptUtils.executeScript(
                                    eq("java -cp mydeps MainClass"), eq(Collections.emptyList())),
                    times(1));
        }
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
