package org.codejive.jpm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
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

    @BeforeEach
    void setUp() {
        originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalDir);
    }

    @Test
    void testPerformanceOptimizationNoDepsVariable() throws IOException {
        // Create app.yml with action that doesn't use {{deps}}
        createAppYmlWithSimpleAction();

        // Mock ScriptUtils to verify classpath is empty when no {{deps}} variable
        try (MockedStatic<ScriptUtils> mockedScriptUtils = Mockito.mockStatic(ScriptUtils.class)) {
            mockedScriptUtils
                    .when(() -> ScriptUtils.executeScript(anyString(), any(List.class), any()))
                    .thenReturn(0);

            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("do", "simple");

            assertThat(exitCode).isEqualTo(0);

            // Verify that executeScript was called with empty classpath
            mockedScriptUtils.verify(
                    () ->
                            ScriptUtils.executeScript(
                                    eq("true"),
                                    eq(Collections.emptyList()),
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
                    .when(() -> ScriptUtils.executeScript(anyString(), any(List.class), any()))
                    .thenReturn(0);

            CommandLine cmd = Main.getCommandLine();

            // Test exact match - should resolve classpath
            cmd.execute("do", "exact");
            mockedScriptUtils.verify(
                    () ->
                            ScriptUtils.executeScript(
                                    contains("{{deps}}"), eq(Collections.emptyList()), any()),
                    times(1));

            mockedScriptUtils.clearInvocations();

            // Test different case - should NOT resolve classpath
            cmd.execute("do", "different");
            mockedScriptUtils.verify(
                    () ->
                            ScriptUtils.executeScript(
                                    eq("java -cp ${DEPS} MainClass"),
                                    eq(Collections.emptyList()),
                                    eq(Collections.emptyList())),
                    times(1));

            mockedScriptUtils.clearInvocations();

            // Test substring - should NOT resolve classpath
            cmd.execute("do", "substring");
            mockedScriptUtils.verify(
                    () ->
                            ScriptUtils.executeScript(
                                    eq("java -cp mydeps MainClass"),
                                    eq(Collections.emptyList()),
                                    eq(Collections.emptyList())),
                    times(1));
        }
    }

    private void createAppYmlWithSimpleAction() throws IOException {
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  simple: \"true\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }
}
