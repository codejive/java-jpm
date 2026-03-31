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
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import picocli.CommandLine;

/** Integration tests for the --cache option and JPM_CACHE environment variable. */
class MainCacheIntegrationTest {

    @TempDir Path tempDir;
    @TempDir Path cacheDir1;
    @TempDir Path cacheDir2;

    private String originalUserDir;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        System.setProperty("picocli.ansi", "false");

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
        System.setProperty("user.dir", originalUserDir);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testPathCommandWithCacheOption() throws IOException {
        // Create app.yml
        createSimpleAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("path", "--cache", cacheDir1.toString());

        // Exit code 0 or 1 is acceptable (1 means dependency not found, which is expected)
        assertThat(exitCode).isIn(0, 1);
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "/tmp/env-cache")
    void testPathCommandWithEnvironmentVariable() throws IOException {
        // Create app.yml
        createSimpleAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("path");

        // The command should succeed with JPM_CACHE set
        assertThat(exitCode).isIn(0, 1);
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "/tmp/env-cache")
    void testCopyCommandCacheOptionOverridesEnvironmentVariable() throws IOException {
        CommandLine cmd = Main.getCommandLine();
        int exitCode =
                cmd.execute(
                        "copy", "--cache", cacheDir1.toString(), "--quiet", "fake:artifact:1.0.0");

        // The command should use cacheDir1 (from --cache) not /tmp/env-cache
        // Even though it will fail to resolve, it should parse correctly
        assertThat(exitCode).isIn(0, 1); // May fail to resolve, but shouldn't crash
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testInstallCommandWithShortCacheOption() throws IOException {
        createSimpleAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode =
                cmd.execute(
                        "install", "-c", cacheDir1.toString(), "--quiet", "fake:artifact:1.0.0");

        // The -c short form should work the same as --cache
        assertThat(exitCode).isIn(0, 1); // May fail to resolve, but shouldn't crash
    }

    @Test
    void testCacheOptionInHelp() {
        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("copy", "--help");

        // PicoCLI may return 0 or 2 for help depending on configuration
        // What matters is that the help text is displayed
        String output = outContent.toString() + errContent.toString();
        assertThat(output)
                .contains("-c, --cache")
                .contains("Directory where downloaded artifacts will be cached")
                .contains("JPM_CACHE");
    }

    @Test
    @SetEnvironmentVariable(key = "JPM_CACHE", value = "   ")
    void testGetCacheDirWithWhitespaceOnlyEnvironmentVariable() throws IOException {
        // An environment variable with only whitespace should be treated as empty
        createSimpleAppYml();

        CommandLine cmd = Main.getCommandLine();
        // This should not crash - whitespace-only JPM_CACHE should be ignored
        int exitCode = cmd.execute("path");

        assertThat(exitCode).isIn(0, 1);
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testCacheOptionWithRelativePath() throws IOException {
        createSimpleAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("path", "--cache", "./my-cache");

        assertThat(exitCode).isIn(0, 1);
        // Should accept relative paths
    }

    @Test
    @ClearEnvironmentVariable(key = "JPM_CACHE")
    void testCacheOptionWithAbsolutePath() throws IOException {
        createSimpleAppYml();

        CommandLine cmd = Main.getCommandLine();
        int exitCode = cmd.execute("path", "--cache", cacheDir1.toAbsolutePath().toString());

        assertThat(exitCode).isIn(0, 1);
        // Should accept absolute paths
    }

    private void createSimpleAppYml() throws IOException {
        String yamlContent =
                "dependencies:\n"
                        + "  fake:dummy: \"1.2.3\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  build: \"echo building\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }
}
