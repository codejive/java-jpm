package org.codejive.jpm.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Utility class for executing scripts with path conversion and variable substitution. */
public class ScriptUtils {

    /**
     * Executes a script command with variable substitution and path conversion.
     *
     * @param command The command to execute
     * @param classpath The classpath to use for {{deps}} substitution
     * @param verbose If true, prints the command before execution
     * @return The exit code of the executed command
     * @throws IOException if an error occurred during execution
     * @throws InterruptedException if the execution was interrupted
     */
    public static int executeScript(String command, List<Path> classpath, boolean verbose)
            throws IOException, InterruptedException {
        // We do a first pass of processing just to know the size of the command
        String tmpCommand = processCommand(command, classpath, null);
        boolean useArgsFiles =
                (isWindows() && tmpCommand.length() > 8000)
                        || (!isWindows() && tmpCommand.length() > 32000);

        command = suggestSubstitutions(command);

        try (ArgsFiles argsFiles = new ArgsFiles()) {
            // Process the command for variable substitution and path conversion
            String processedCommand =
                    processCommand(command, classpath, useArgsFiles ? argsFiles::create : null);
            if (verbose) {
                System.out.println("> " + processedCommand);
            }

            // Prepare the command for execution using a shell or cmd
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
    }

    public static String quoteArgument(String arg) {
        return arg;
    }

    static String suggestSubstitutions(String command) {
        if (!usingSubstitutions(command)) {
            // First try to parse the command
            CommandsParser parser = new CommandsParser(command);
            CommandsParser.Commands commands = parser.parse();
            if (commands != null) {
                command = suggestCommandsSubstitutions(commands);
            }
        }
        return command;
    }

    // Is the command using any substitutions? (we ignore {{deps}} here)
    private static boolean usingSubstitutions(String command) {
        command = command.replace("{]}", "\u0007");
        Pattern pattern = Pattern.compile("\\{([/:;~]|\\./.*|~/.*)}|@\\[.*]");
        return pattern.matcher(command).find();
    }

    private static String suggestCommandsSubstitutions(CommandsParser.Commands commands) {
        StringBuilder cmd = new StringBuilder();
        for (CommandsParser.Node node : commands.elements) {
            if (node instanceof CommandsParser.Command) {
                cmd.append(suggestCommandSubstitutions((CommandsParser.Command) node));
            } else if (node instanceof CommandsParser.Group) {
                CommandsParser.Group group = (CommandsParser.Group) node;
                String groupStr = suggestCommandsSubstitutions(group.commands);
                cmd.append("(").append(groupStr).append(")");
            } else if (node instanceof CommandsParser.Separator) {
                CommandsParser.Separator sep = (CommandsParser.Separator) node;
                if (sep.type.equals(";")) {
                    cmd.append(" {;} ");
                } else {
                    cmd.append(" ").append(sep.type).append(" ");
                }
            }
        }
        return cmd.toString();
    }

    static String suggestCommandSubstitutions(CommandsParser.Command command) {
        StringBuilder cmd = new StringBuilder();
        if (command.words.isEmpty()) {
            return "";
        }

        for (int i = 0; i < command.words.size(); i++) {
            String w = command.words.get(i);
            String neww = suggestClassPathSubstitution(w);
            if (neww == null) {
                neww = suggestPathSubstitution(w);
            }
            if (neww != null) {
                command.words.set(i, neww);
            }
        }

        // For commands that support class paths, we look for {{deps}} and
        // suggest putting @[ ... ] around the entire argument
        for (int i = 0; i < command.words.size(); i++) {
            String w = command.words.get(i);
            String neww = null;
            if (i == 0) {
                if (w.startsWith("@")) {
                    neww = w.substring(1);
                } else if (supportsArgsFiles(w)) {
                    neww = w;
                }
            } else if (w.contains("{{deps}}")) {
                // Let's add @[ ... ] around the entire argument
                neww = "@[" + w.replace("]", "{]}") + "]";
            }
            if (neww != null) {
                command.words.set(i, neww);
            }
        }

        String commandStr = String.join(" ", command.words);
        cmd.append(commandStr);
        return cmd.toString();
    }

    private static boolean supportsArgsFiles(String cmd) {
        Path p = FileUtils.safePath(cmd);
        if (p == null) {
            return false;
        }
        String name = p.getFileName().toString().toLowerCase(Locale.ENGLISH);
        if (name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".cmd")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name.equals("java")
                || name.equals("javac")
                || name.equals("javadoc")
                || name.equals("javap")
                || name.equals("jdeps")
                || name.equals("jmod");
    }

    private static String suggestPathSubstitution(String path) {
        Path p = FileUtils.safePath(path);
        if (p == null || p.isAbsolute() || p.getNameCount() < 2) {
            return null;
        }
        // Looks like a relative path, let's suggest {./...} or {~/...}
        if (p.startsWith("~")) {
            // Do nothing
        } else if (!p.startsWith(".")) {
            path = "./" + path;
        }
        return "{" + path + "}";
    }

    private static String suggestClassPathSubstitution(String classpath) {
        String[] parts = classpath.split(":");
        if (parts.length <= 1) {
            return null;
        }
        // First see if this plausibly looks like a classpath
        // This requires at least one part to be a relative path with
        // at least two parts (i.e. have at least one / in it)
        // This does mean that it won't suggest substitutions for
        // classpaths that are all single-name parts (e.g. "lib:ext").
        if (!classpath.contains("{{deps}}")) {
            boolean hasAtLeastOneComplexPart = false;
            for (String path : parts) {
                Path p = FileUtils.safePath(path);
                if (p == null || p.isAbsolute()) {
                    return null;
                }
                if (p.getNameCount() >= 2) {
                    hasAtLeastOneComplexPart = true;
                }
            }
            if (!hasAtLeastOneComplexPart) {
                return null;
            }
        }
        // Now do the actual conversion
        StringBuilder sb = new StringBuilder();
        for (String path : parts) {
            Path p = FileUtils.safePath(path);
            if (sb.length() == 0) {
                // Looks like a relative path, let's suggest {./...} or {~/...}
                if (p.startsWith("~")) {
                    // Do nothing
                } else if (!p.startsWith(".")) {
                    sb.append("./");
                }
            } else {
                sb.append(":");
            }
            sb.append(path);
        }
        return "{" + sb.toString() + "}";
    }

    /**
     * Processes a command by performing variable substitution and path conversion.
     *
     * @param command The raw command
     * @param classpath The classpath to use for {{deps}} substitution
     * @param argsFileCreator A function that creates an args file given its content, or null to not
     *     use args files
     * @return The processed command
     */
    static String processCommand(
            String command, List<Path> classpath, Function<String, Path> argsFileCreator) {
        String result = command;

        // Substitute {{deps}} with the classpath
        result = substituteDeps(result, classpath);

        // Find all occurrences of {./...} and {~/...} and replace them with os paths
        // This also handles classpath constructs with multiple paths separated by :
        result = substitutePaths(result);

        // Special replacements for dealing with paths and classpath separators
        // in a cross-platform way
        result = result.replace("{/}", File.separator);
        result = result.replace("{:}", File.pathSeparator);
        result =
                result.replace(
                        "{~}",
                        isWindows() ? Paths.get(System.getProperty("user.home")).toString() : "~");

        // Special replacement {;} for dealing with multi-command actions in a
        // cross-platform way
        result = result.replace("{;}", isWindows() ? "&" : ";");

        // Now we go look for any @[ ... ] and write the contents to an args file
        // replacing the construct with an @ followed by the args file path
        result = substituteArgsFiles(result, argsFileCreator);

        return result;
    }

    private static String substituteDeps(String command, List<Path> classpath) {
        if (command.contains("{{deps}}")) {
            String classpathStr = "";
            if (classpath != null && !classpath.isEmpty()) {
                classpathStr =
                        classpath.stream()
                                .map(Path::toString)
                                .collect(Collectors.joining(File.pathSeparator));
            }
            command = command.replace("{{deps}}", classpathStr);
        }
        return command;
    }

    private static String substitutePaths(String command) {
        Pattern pattern = Pattern.compile("\\{([.~]/[^}]*)}");
        Matcher matcher = pattern.matcher(command);
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
            } else {
                // If we're not on Windows, we assume the path is already correct
                replacedPath = path;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacedPath));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String substituteArgsFiles(
            String command, Function<String, Path> argsFileCreator) {
        // First we replace any {]} with a placeholder to avoid messing up the regex
        String tmpCommand = command.replace("{]}", "\u0007");
        // Now we go look for any @[ ... ] and write the contents to an args file
        // replacing the construct with an @ followed by the args file path
        Pattern pattern = Pattern.compile("@\\[([^]]*)]");
        Matcher matcher = pattern.matcher(tmpCommand);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            // Extract the contents inside @[ ... ]
            String argsContent = matcher.group(1).trim();
            if (argsFileCreator != null) {
                // Write contents to a file and replace @[ ... ] with @<args-file-path>
                String path = argsFileCreator.apply(argsContent).toString();
                matcher.appendReplacement(sb, Matcher.quoteReplacement("@" + path));
            } else {
                // Just remove the @[ ... ] and keep the contents as-is
                matcher.appendReplacement(sb, Matcher.quoteReplacement(argsContent));
            }
        }
        matcher.appendTail(sb);
        String result = sb.toString();
        // Now we put back any ] we might have had
        result = result.replace("\u0007", "{]}");
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

    static class ArgsFiles implements AutoCloseable {
        final List<Path> files = new ArrayList<>();

        public Path create(String content) {
            try {
                File argsFile = File.createTempFile("jpm-args-", ".txt");
                argsFile.deleteOnExit();
                Path path = argsFile.toPath();
                Files.write(path, content.getBytes());
                files.add(path);
                return path;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create args file", e);
            }
        }

        @Override
        public void close() {
            for (Path file : files) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    // Ignore
                }
            }
            files.clear();
        }
    }
}
