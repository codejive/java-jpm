package org.codejive.jpm.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Utility class for executing scripts with path conversion and variable substitution. */
public class ScriptUtils {

    /**
     * Executes a script command with variable substitution and path conversion.
     *
     * @param command The command to execute
     * @param classpath The classpath to use for {{deps}} substitution
     * @return The exit code of the executed command
     * @throws IOException if an error occurred during execution
     * @throws InterruptedException if the execution was interrupted
     */
    public static int executeScript(String command, List<Path> classpath)
            throws IOException, InterruptedException {
        String processedCommand = processCommand(command, classpath);
        String[] commandTokens =
                isWindows()
                        ? new String[] {"cmd.exe", "/c", processedCommand}
                        : new String[] {"/bin/sh", "-c", processedCommand};
        ProcessBuilder pb = new ProcessBuilder(commandTokens);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String cmdOutput = br.lines().collect(Collectors.joining("\n"));

        return p.waitFor();
    }

    /**
     * Processes a command by performing variable substitution and path conversion.
     *
     * @param command The raw command
     * @param classpath The classpath to use for {{deps}} substitution
     * @return The processed command
     */
    static String processCommand(String command, List<Path> classpath) {
        String result = command;

        // Substitute {{deps}} with the classpath
        if (result.contains("{{deps}}")) {
            String classpathStr = "";
            if (classpath != null && !classpath.isEmpty()) {
                classpathStr =
                        classpath.stream()
                                .map(Path::toString)
                                .collect(Collectors.joining(File.pathSeparator));
            }
            result = result.replace("{{deps}}", classpathStr);
        }
        result = result.replace("{/}", File.separator);
        result = result.replace("{:}", File.pathSeparator);

        return result;
    }

    /** Checks if the current operating system is Windows. */
    public static boolean isWindows() {
        String os =
                System.getProperty("os.name")
                        .toLowerCase(Locale.ENGLISH)
                        .replaceAll("[^a-z0-9]+", "");
        return os.startsWith("win");
    }
}
