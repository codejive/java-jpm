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

        // Split command into tokens for ProcessBuilder
        String[] commandTokens = parseCommand(processedCommand);

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
    private static String processCommand(String command, List<Path> classpath) {
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

        // Convert Unix-style paths to Windows if needed
        if (isWindows()) {
            result = convertPathsForWindows(result);
        }

        return result;
    }

    /**
     * Converts Unix-style paths to Windows format. This is a simple heuristic that looks for
     * patterns like "deps/*" and converts them.
     */
    private static String convertPathsForWindows(String command) {
        // Convert forward slashes in path-like patterns to backslashes
        // This is a simple heuristic - in a real implementation you might want more sophisticated
        // logic
        return command.replaceAll("([a-zA-Z0-9_.-]+)/\\*", "$1\\\\*")
                .replaceAll("([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)", "$1\\\\$2");
    }

    /**
     * Parses a command string into tokens for ProcessBuilder. This is a simple implementation that
     * splits on spaces while respecting quotes.
     */
    private static String[] parseCommand(String command) {
        // Simple parsing - for a full implementation you'd want proper shell parsing
        java.util.List<String> tokens = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentToken = new StringBuilder();

        for (char c : command.toCharArray()) {
            if (c == '"' || c == '\'') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens.toArray(new String[0]);
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
