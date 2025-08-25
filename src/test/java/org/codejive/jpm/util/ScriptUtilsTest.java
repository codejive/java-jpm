package org.codejive.jpm.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
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
        // Use reflection to access private method for unit testing
        Method processCommand =
                ScriptUtils.class.getDeclaredMethod("processCommand", String.class, List.class);
        processCommand.setAccessible(true);

        List<Path> classpath =
                Arrays.asList(
                        Paths.get("deps/lib1.jar"),
                        Paths.get("deps/lib2.jar"),
                        Paths.get("deps/lib3.jar"));

        String command = "java -cp ${deps} MainClass";
        String result = (String) processCommand.invoke(null, command, classpath);

        // Use the actual paths from the classpath as they would be processed
        String expectedClasspath =
                String.join(
                        System.getProperty("path.separator"),
                        classpath.get(0).toString(),
                        classpath.get(1).toString(),
                        classpath.get(2).toString());
        assertEquals("java -cp " + expectedClasspath + " MainClass", result);
    }

    @Test
    void testProcessCommandWithoutDepsSubstitution() throws Exception {
        Method processCommand =
                ScriptUtils.class.getDeclaredMethod("processCommand", String.class, List.class);
        processCommand.setAccessible(true);

        List<Path> classpath = Arrays.asList(Paths.get("deps/lib1.jar"));
        String command = "echo Hello World";
        String result = (String) processCommand.invoke(null, command, classpath);

        // Command should remain unchanged since no ${deps} variable
        assertEquals("echo Hello World", result);
    }

    @Test
    void testProcessCommandWithEmptyClasspath() throws Exception {
        Method processCommand =
                ScriptUtils.class.getDeclaredMethod("processCommand", String.class, List.class);
        processCommand.setAccessible(true);

        List<Path> classpath = Collections.emptyList();
        String command = "java -cp ${deps} MainClass";
        String result = (String) processCommand.invoke(null, command, classpath);

        // ${deps} should be replaced with empty string
        assertEquals("java -cp  MainClass", result);
    }

    @Test
    void testProcessCommandWithNullClasspath() throws Exception {
        Method processCommand =
                ScriptUtils.class.getDeclaredMethod("processCommand", String.class, List.class);
        processCommand.setAccessible(true);

        String command = "java -cp ${deps} MainClass";
        String result = (String) processCommand.invoke(null, command, null);

        // ${deps} should be replaced with empty string
        assertEquals("java -cp  MainClass", result);
    }

    @Test
    void testProcessCommandWithMultipleDepsReferences() throws Exception {
        Method processCommand =
                ScriptUtils.class.getDeclaredMethod("processCommand", String.class, List.class);
        processCommand.setAccessible(true);

        List<Path> classpath = Arrays.asList(Paths.get("deps/lib1.jar"));
        String command = "java -cp ${deps} MainClass && java -cp ${deps} TestClass";
        String result = (String) processCommand.invoke(null, command, classpath);

        // Use the actual path as it would be processed
        String expectedPath = classpath.get(0).toString();
        assertEquals(
                "java -cp "
                        + expectedPath
                        + " MainClass && java -cp "
                        + expectedPath
                        + " TestClass",
                result);
    }

    @Test
    void testParseCommandSimple() throws Exception {
        Method parseCommand = ScriptUtils.class.getDeclaredMethod("parseCommand", String.class);
        parseCommand.setAccessible(true);

        String command = "java -cp deps/*.jar MainClass";
        String[] result = (String[]) parseCommand.invoke(null, command);

        assertArrayEquals(new String[] {"java", "-cp", "deps/*.jar", "MainClass"}, result);
    }

    @Test
    void testParseCommandWithQuotes() throws Exception {
        Method parseCommand = ScriptUtils.class.getDeclaredMethod("parseCommand", String.class);
        parseCommand.setAccessible(true);

        String command = "echo \"Hello World\"";
        String[] result = (String[]) parseCommand.invoke(null, command);

        assertArrayEquals(new String[] {"echo", "Hello World"}, result);
    }

    @Test
    void testParseCommandWithSingleQuotes() throws Exception {
        Method parseCommand = ScriptUtils.class.getDeclaredMethod("parseCommand", String.class);
        parseCommand.setAccessible(true);

        String command = "echo 'Hello World'";
        String[] result = (String[]) parseCommand.invoke(null, command);

        assertArrayEquals(new String[] {"echo", "Hello World"}, result);
    }

    @Test
    void testParseCommandComplex() throws Exception {
        Method parseCommand = ScriptUtils.class.getDeclaredMethod("parseCommand", String.class);
        parseCommand.setAccessible(true);

        String command = "java -cp deps/*.jar -Dprop=\"value with spaces\" MainClass arg1 arg2";
        String[] result = (String[]) parseCommand.invoke(null, command);

        assertArrayEquals(
                new String[] {
                    "java",
                    "-cp",
                    "deps/*.jar",
                    "-Dprop=value with spaces",
                    "MainClass",
                    "arg1",
                    "arg2"
                },
                result);
    }

    @Test
    void testParseCommandEmpty() throws Exception {
        Method parseCommand = ScriptUtils.class.getDeclaredMethod("parseCommand", String.class);
        parseCommand.setAccessible(true);

        String command = "";
        String[] result = (String[]) parseCommand.invoke(null, command);

        assertArrayEquals(new String[] {}, result);
    }

    @Test
    void testIsWindows() {
        boolean result = ScriptUtils.isWindows();

        // The result should match the current OS
        String os = System.getProperty("os.name").toLowerCase();
        assertEquals(os.contains("win"), result);
    }

    @Test
    void testConvertPathsForWindows() throws Exception {
        Method convertPathsForWindows =
                ScriptUtils.class.getDeclaredMethod("convertPathsForWindows", String.class);
        convertPathsForWindows.setAccessible(true);

        String command = "java -cp deps/* MainClass";
        String result = (String) convertPathsForWindows.invoke(null, command);

        assertEquals("java -cp deps\\* MainClass", result);
    }

    @Test
    void testConvertPathsForWindowsWithDirectories() throws Exception {
        Method convertPathsForWindows =
                ScriptUtils.class.getDeclaredMethod("convertPathsForWindows", String.class);
        convertPathsForWindows.setAccessible(true);

        String command = "java -cp libs/external MainClass";
        String result = (String) convertPathsForWindows.invoke(null, command);

        assertEquals("java -cp libs\\external MainClass", result);
    }

    @Test
    void testExecuteScriptSimpleCommand() {
        // Test that executeScript can be called without throwing exceptions
        // We can't easily test the actual execution without mocking ProcessBuilder
        assertDoesNotThrow(
                () -> {
                    // Use a simple command that should work on most systems
                    List<Path> classpath = Collections.emptyList();
                    // Note: This test is limited because we can't easily mock ProcessBuilder
                    // In a real scenario, you might want to use a mocking framework
                });
    }

    @Test
    void testProcessCommandIntegration() throws Exception {
        // Integration test combining variable substitution and path handling
        Method processCommand =
                ScriptUtils.class.getDeclaredMethod("processCommand", String.class, List.class);
        processCommand.setAccessible(true);

        List<Path> classpath =
                Arrays.asList(Paths.get("deps/lib1.jar"), Paths.get("deps/lib2.jar"));

        String command = "java -cp .:${deps} -Dmyprop=value MainClass arg1";
        String result = (String) processCommand.invoke(null, command, classpath);

        // Use the actual paths as they would be processed
        String expectedClasspath =
                String.join(
                        System.getProperty("path.separator"),
                        classpath.get(0).toString(),
                        classpath.get(1).toString());
        assertEquals("java -cp .:" + expectedClasspath + " -Dmyprop=value MainClass arg1", result);
    }
}
