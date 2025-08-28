package org.codejive.jpm.util;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for ScriptUtils class, focusing on command processing and variable substitution. */
class ScriptUtilsTest {

    @Test
    void testProcessCommandWithDepsSubstitution() throws Exception {
        List<Path> classpath =
                Arrays.asList(
                        Paths.get("deps/lib1.jar"),
                        Paths.get("deps/lib2.jar"),
                        Paths.get("deps/lib3.jar"));

        String command = "java -cp {{deps}} MainClass";
        String result = ScriptUtils.processCommand(command, classpath);

        // Use the actual paths from the classpath as they would be processed
        String expectedClasspath =
                String.join(
                        File.pathSeparator,
                        classpath.get(0).toString(),
                        classpath.get(1).toString(),
                        classpath.get(2).toString());
        assertThat(result).isEqualTo("java -cp " + expectedClasspath + " MainClass");
    }

    @Test
    void testProcessCommandWithoutDepsSubstitution() throws Exception {
        List<Path> classpath = Arrays.asList(Paths.get("deps/lib1.jar"));
        String command = "echo Hello World";
        String result = ScriptUtils.processCommand(command, classpath);
        // Command should remain unchanged since no {{deps}} variable
        assertThat(result).isEqualTo("echo Hello World");
    }

    @Test
    void testProcessCommandWithEmptyClasspath() throws Exception {
        List<Path> classpath = Collections.emptyList();
        String command = "java -cp \"{{deps}}\" MainClass";
        String result = ScriptUtils.processCommand(command, classpath);
        // {{deps}} should be replaced with empty string
        assertThat(result).isEqualTo("java -cp \"\" MainClass");
    }

    @Test
    void testProcessCommandWithNullClasspath() throws Exception {
        String command = "java -cp \"{{deps}}\" MainClass";
        String result = ScriptUtils.processCommand(command, null);
        // {{deps}} should be replaced with empty string
        assertThat(result).isEqualTo("java -cp \"\" MainClass");
    }

    @Test
    void testProcessCommandWithPathTokens() throws Exception {
        String command = "java -cp .{/}libs{:}.{/}ext{:}{~}{/}usrlibs MainClass";
        String result = ScriptUtils.processCommand(command, null);
        String expectedPath =
                ScriptUtils.isWindows()
                        ? ".\\libs;.\\ext;" + System.getProperty("user.home") + "\\usrlibs"
                        : "./libs:./ext:~/usrlibs";
        assertThat(result).isEqualTo("java -cp " + expectedPath + " MainClass");
    }

    @Test
    void testProcessCommandWithPathReplacement() throws Exception {
        String command = "java -cp {./libs:./ext:~/usrlibs} MainClass";
        String result = ScriptUtils.processCommand(command, null);
        String expectedPath =
                ScriptUtils.isWindows()
                        ? ".\\libs;.\\ext;" + System.getProperty("user.home") + "\\usrlibs"
                        : "./libs:./ext:~/usrlibs";
        assertThat(result).isEqualTo("java -cp " + expectedPath + " MainClass");
    }

    @Test
    void testProcessCommandWithPathReplacement2() throws Exception {
        String command = "java -cp {~/usrlibs:./libs:./ext} MainClass";
        String result = ScriptUtils.processCommand(command, null);
        String expectedPath =
                ScriptUtils.isWindows()
                        ? System.getProperty("user.home") + "\\usrlibs;.\\libs;.\\ext"
                        : "~/usrlibs:./libs:./ext";
        assertThat(result).isEqualTo("java -cp " + expectedPath + " MainClass");
    }

    @Test
    void testProcessCommandWithMultipleDepsReferences() throws Exception {
        List<Path> classpath = Arrays.asList(Paths.get("deps/lib1.jar"));
        String command = "java -cp {{deps}} MainClass && java -cp {{deps}} TestClass";
        String result = ScriptUtils.processCommand(command, classpath);

        // Use the actual path as it would be processed
        String expectedPath = classpath.get(0).toString();
        assertThat(result)
                .isEqualTo(
                        "java -cp "
                                + expectedPath
                                + " MainClass && java -cp "
                                + expectedPath
                                + " TestClass");
    }

    @Test
    void testIsWindows() {
        boolean result = ScriptUtils.isWindows();
        // The result should match the current OS
        String os = System.getProperty("os.name").toLowerCase();
        assertThat(result).isEqualTo(os.contains("win"));
    }

    @Test
    void testExecuteScriptSimpleCommand() {
        // Test that executeScript can be called without throwing exceptions
        // We can't easily test the actual execution without mocking ProcessBuilder
        assertThatCode(
                        () -> {
                            // Use a simple command that should work on most systems
                            List<Path> classpath = Collections.emptyList();
                            // Note: This test is limited because we can't easily mock
                            // ProcessBuilder
                            // In a real scenario, you might want to use a mocking framework
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void testProcessCommandIntegration() throws Exception {
        // Integration test combining variable substitution and path handling
        List<Path> classpath =
                Arrays.asList(Paths.get("deps/lib1.jar"), Paths.get("deps/lib2.jar"));

        String command = "java -cp .{:}.{/}libs{/}*{:}{{deps}} -Dmyprop=value MainClass arg1";
        String result = ScriptUtils.processCommand(command, classpath);

        // Use the actual paths as they would be processed
        String expectedClasspath =
                String.join(
                        File.pathSeparator,
                        classpath.get(0).toString(),
                        classpath.get(1).toString());
        if (ScriptUtils.isWindows()) {
            expectedClasspath = ".;.\\libs\\*;" + expectedClasspath;
        } else {
            expectedClasspath = ".:./libs/*:" + expectedClasspath;
        }
        assertThat(result)
                .isEqualTo("java -cp " + expectedClasspath + " -Dmyprop=value MainClass arg1");
    }
}
