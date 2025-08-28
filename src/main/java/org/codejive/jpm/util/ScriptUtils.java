package org.codejive.jpm.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Utility class for executing scripts with path conversion and variable substitution. */
public class ScriptUtils {

    /**
     * Executes a script command with variable substitution and path conversion.
     *
     * @param command The command to execute
     * @param args The arguments to pass to the command
     * @param classpath The classpath to use for {{deps}} substitution
     * @param verbose If true, prints the command before execution
     * @return The exit code of the executed command
     * @throws IOException if an error occurred during execution
     * @throws InterruptedException if the execution was interrupted
     */
    public static int executeScript(
            String command, List<String> args, List<Path> classpath, boolean verbose)
            throws IOException, InterruptedException {
        if (args != null && !args.isEmpty()) {
            command +=
                    args.stream()
                            .map(ScriptUtils::quoteArgument)
                            .collect(Collectors.joining(" ", " ", ""));
        }

        String processedCommand = processCommand(command, classpath);
        if (verbose) {
            System.out.println("> " + processedCommand);
        }
        String[] commandTokens =
                isWindows()
                        ? new String[] {"cmd.exe", "/c", processedCommand}
                        : new String[] {"/bin/sh", "-c", processedCommand};
        ProcessBuilder pb = new ProcessBuilder(commandTokens);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        br.lines().forEach(System.out::println);
        return p.waitFor();
    }

    static String quoteArgument(String arg) {
        return arg;
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

        // Find all occurrences of {./...} and {~/...} and replace them with os paths
        // This also handles classpath constructs with multiple paths separated by :
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([.~]/[^}]*)}");
        java.util.regex.Matcher matcher = pattern.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            String replacedPath;
            if (isWindows()) {
                String[] cp = path.split(":");
                replacedPath =
                        Arrays.stream(cp)
                                .map(
                                        p -> {
                                            if (p.startsWith("~/")) {
                                                return Paths.get(
                                                                System.getProperty("user.home"),
                                                                p.substring(2))
                                                        .toString();
                                            } else {
                                                return Paths.get(p).toString();
                                            }
                                        })
                                .collect(Collectors.joining(File.pathSeparator));
                replacedPath = replacedPath.replace("\\", "\\\\");
            } else {
                // If we're not on Windows, we assume the path is already correct
                replacedPath = path;
            }
            matcher.appendReplacement(sb, replacedPath);
        }
        matcher.appendTail(sb);
        result = sb.toString();

        // Special replacements for dealing with paths and classpath separators
        // in cross-platform way
        result = result.replace("{/}", File.separator);
        result = result.replace("{:}", File.pathSeparator);
        result =
                result.replace(
                        "{~}",
                        isWindows() ? Paths.get(System.getProperty("user.home")).toString() : "~");

        // Special replacement {;} for dealing with multi-command actions in a
        // cross-platform way
        result = result.replace("{;}", isWindows() ? "&" : ";");

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
