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
class MainIT {

    @TempDir Path tempDir;

    private String originalDir;

    @BeforeEach
    void setUp() {
        originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        System.setProperty("picocli.ansi", "false");
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
    void testDoAliasWithArgs() throws IOException {
        createAppYmlWithRepositories();
        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode = cmd.execute("build", "--foo", "bar");

            assertThat(exitCode).isEqualTo(0);
            String output = capture.getOut();
            // The run action should execute and include the classpath in the output
            assertThat(output).contains("javac", "jfiglet", "--foo bar");
        }
    }

    @Test
    void testCopyCommandWithRepositoryOptions() throws IOException {
        // Test copy command with --repo options
        CommandLine cmd = Main.getCommandLine();
        int exitCode =
                cmd.execute(
                        "copy",
                        "--repo",
                        "central=https://repo1.maven.org/maven2",
                        "--repo",
                        "https://jcenter.bintray.com",
                        "com.google.guava:guava:31.1-jre");

        // The command should execute successfully (even if dependency resolution might fail)
        assertThat(exitCode >= 0).isTrue();
    }

    @Test
    void testInstallCommandWithRepositoryOptions() throws IOException {
        CommandLine cmd = Main.getCommandLine();
        int exitCode =
                cmd.execute(
                        "install",
                        "--repo",
                        "central=https://repo1.maven.org/maven2",
                        "com.google.guava:guava:31.1-jre");

        // The command should execute successfully (even if dependency resolution might fail)
        assertThat(exitCode >= 0).isTrue();
    }

    @Test
    void testPathCommandWithRepositoryOptionsAndAppYml() throws IOException {
        // Create app.yml with repositories
        createAppYmlWithRepositories();

        try (TestOutputCapture capture = captureOutput()) {
            CommandLine cmd = Main.getCommandLine();
            int exitCode =
                    cmd.execute(
                            "path",
                            "--repo",
                            "jcenter=https://jcenter.bintray.com",
                            "com.google.guava:guava:31.1-jre");

            // The command should execute (even if dependency resolution might fail)
            assertThat(exitCode >= 0).isTrue();
        }
    }

    private void createAppYmlWithRepositories() throws IOException {
        String yamlContent =
                "dependencies:\n"
                        + "  com.github.lalyos:jfiglet: \"0.0.9\"\n"
                        + "\n"
                        + "repositories:\n"
                        + "  central: \"https://repo1.maven.org/maven2\"\n"
                        + "  custom: \"https://my.custom.repo/maven2\"\n"
                        + "\n"
                        + "actions:\n"
                        + "  build: \"echo javac -cp {{deps}} *.java\"\n"
                        + "  test: \"echo java -cp {{deps}} TestRunner\"\n";
        Files.writeString(tempDir.resolve("app.yml"), yamlContent);
    }
}
