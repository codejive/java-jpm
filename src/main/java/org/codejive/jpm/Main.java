// spotless:off Dependencies for JBang
//DEPS eu.maveniverse.maven.mima:context:2.4.15 eu.maveniverse.maven.mima.runtime:standalone-static:2.4.15
//DEPS info.picocli:picocli:4.7.6
//DEPS org.yaml:snakeyaml:2.3
//DEPS org.jline:jline-console-ui:3.29.0 org.jline:jline-terminal-jni:3.29.0
//DEPS org.slf4j:slf4j-api:2.0.13 org.slf4j:slf4j-simple:2.0.13
//SOURCES Jpm.java json/AppInfo.java util/FileUtils.java util/ResolverUtils.java util/SearchUtils.java
//SOURCES util/SearchResult.java util/SyncStats.java util/Version.java
// spotless:on

package org.codejive.jpm;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.codejive.jpm.util.SyncStats;
import org.codejive.jpm.util.Version;
import org.jline.consoleui.elements.InputValue;
import org.jline.consoleui.elements.ListChoice;
import org.jline.consoleui.elements.PageSizeType;
import org.jline.consoleui.elements.PromptableElementIF;
import org.jline.consoleui.elements.items.ListItemIF;
import org.jline.consoleui.elements.items.impl.ListItem;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.ListResult;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

/** Main class for the jpm command line tool. */
@Command(
        name = "jpm",
        mixinStandardHelpOptions = true,
        versionProvider = Version.class,
        description = "Simple command line tool for managing Maven artifacts",
        subcommands = {
            Main.Copy.class,
            Main.Search.class,
            Main.Install.class,
            Main.PrintPath.class,
            Main.Do.class,
            Main.Clean.class,
            Main.Build.class,
            Main.Run.class,
            Main.Test.class
        })
public class Main {

    @Command(
            name = "copy",
            aliases = {"c"},
            description =
                    "Resolves one or more artifacts and copies them and all their dependencies to a target directory. "
                            + "By default jpm will try to create symbolic links to conserve space.\n\n"
                            + "Example:\n  jpm copy org.apache.httpcomponents:httpclient:4.5.14\n")
    static class Copy implements Callable<Integer> {
        @Mixin QuietMixin quietMixin;
        @Mixin ArtifactsMixin artifactsMixin;

        @Option(
                names = {"-s", "--sync"},
                description =
                        "Makes sure the target directory will only contain the mentioned artifacts and their dependencies, possibly removing other files present in the directory",
                defaultValue = "false")
        private boolean sync;

        @Override
        public Integer call() throws Exception {
            SyncStats stats =
                    Jpm.builder()
                            .directory(artifactsMixin.depsMixin.directory)
                            .noLinks(artifactsMixin.depsMixin.noLinks)
                            .build()
                            .copy(artifactsMixin.artifactNames, sync);
            if (!quietMixin.quiet) {
                printStats(stats);
            }
            return (Integer) 0;
        }
    }

    @Command(
            name = "search",
            aliases = {"s"},
            description =
                    "Without arguments this command will start an interactive search asking the user to "
                            + "provide details of the artifact to look for and the actions to take. When provided "
                            + "with an argument this command finds and returns the names of those artifacts that "
                            + "match the given (partial) name.\n\n"
                            + "Example:\n  jpm search httpclient\n")
    static class Search implements Callable<Integer> {
        @Mixin QuietMixin quietMixin;
        @Mixin DepsMixin depsMixin;

        @Option(
                names = {"-i", "--interactive"},
                description = "Interactively search and select artifacts to install",
                defaultValue = "false")
        private boolean interactive;

        @Option(
                names = {"-m", "--max"},
                description = "Maximum number of results to return")
        private Integer max;

        @Parameters(
                paramLabel = "artifactPattern",
                description = "Partial or full artifact name to search for.",
                defaultValue = "")
        private String artifactPattern;

        @Override
        public Integer call() throws Exception {
            if (interactive || artifactPattern == null || artifactPattern.isEmpty()) {
                if (max == null) {
                    max = (Integer) 100;
                }
                try (Terminal terminal = TerminalBuilder.builder().build()) {
                    while (true) {
                        ConsolePrompt.UiConfig cfg = new ConsolePrompt.UiConfig();
                        cfg.setCancellableFirstPrompt(true);
                        ConsolePrompt prompt = new ConsolePrompt(null, terminal, cfg);
                        Map<String, PromptResultItemIF> result = prompt.prompt(this::nextQuestion);
                        if (result.isEmpty()) {
                            break;
                        }
                        String selectedArtifact = getSelectedId(result, "item");
                        String artifactAction = getSelectedId(result, "action");
                        if ("install".equals(artifactAction)) {
                            SyncStats stats =
                                    Jpm.builder()
                                            .directory(depsMixin.directory)
                                            .noLinks(depsMixin.noLinks)
                                            .build()
                                            .install(new String[] {selectedArtifact});
                            if (!quietMixin.quiet) {
                                printStats(stats);
                            }
                        } else if ("copy".equals(artifactAction)) {
                            SyncStats stats =
                                    Jpm.builder()
                                            .directory(depsMixin.directory)
                                            .noLinks(depsMixin.noLinks)
                                            .build()
                                            .copy(new String[] {selectedArtifact}, false);
                            if (!quietMixin.quiet) {
                                printStats(stats);
                            }
                        } else { // quit
                            break;
                        }
                        String finalAction = selectFinalAction(prompt);
                        if (!"again".equals(finalAction)) {
                            break;
                        }
                        artifactPattern = null;
                    }
                }
            } else {
                if (max == null) {
                    max = (Integer) 20;
                }
                String[] artifactNames = search(artifactPattern);
                if (artifactNames.length > 0) {
                    Arrays.stream(artifactNames).forEach(System.out::println);
                }
            }
            return (Integer) 0;
        }

        String[] search(String artifactPattern) {
            try {
                return Jpm.builder()
                        .directory(depsMixin.directory)
                        .noLinks(depsMixin.noLinks)
                        .build()
                        .search(artifactPattern, Math.min(max, 200));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        List<PromptableElementIF> nextQuestion(Map<String, PromptResultItemIF> results) {
            String pattern;
            if (artifactPattern == null || artifactPattern.isEmpty()) {
                if (!results.containsKey("input")) {
                    return List.of(stringElement("Search for:"));
                }
                pattern = results.get("input").getResult();
            } else {
                pattern = artifactPattern;
            }

            if (!results.containsKey("item")) {
                String[] artifactNames = search(pattern);
                return List.of(selectElement("Select artifact:", artifactNames));
            }

            if (!results.containsKey("action")) {
                return List.of(selectArtifactActionElement());
            } else if ("version".equals(getSelectedId(results, "action"))) {
                results.remove("action");
                pattern = getSelectedId(results, "item");
                String[] artifactNames = search(pattern);
                return List.of(selectElement("Select version:", artifactNames));
            }

            return null;
        }

        InputValue stringElement(String message) {
            return new InputValue("input", message);
        }

        ListChoice selectElement(String message, String[] items) {
            List<ListItemIF> itemList =
                    Arrays.stream(items)
                            .map(it -> new ListItem(it, it))
                            .collect(Collectors.toList());
            return new ListChoice(message, "item", 10, PageSizeType.ABSOLUTE, itemList);
        }

        ListChoice selectArtifactActionElement() {
            List<ListItemIF> itemList = new ArrayList<>();
            itemList.add(new ListItem("Download & Install artifact", "install"));
            itemList.add(new ListItem("Download & Copy artifact", "copy"));
            itemList.add(new ListItem("Select different version", "version"));
            itemList.add(new ListItem("Quit", "quit"));
            return new ListChoice("What to do:", "action", 10, PageSizeType.ABSOLUTE, itemList);
        }

        String selectFinalAction(ConsolePrompt prompt) throws IOException {
            PromptBuilder promptBuilder = prompt.getPromptBuilder();
            promptBuilder
                    .createListPrompt()
                    .name("action")
                    .message("Next step:")
                    .newItem("quit")
                    .text("Quit")
                    .add()
                    .newItem("again")
                    .text("Search again")
                    .add()
                    .addPrompt();
            Map<String, PromptResultItemIF> result = prompt.prompt(promptBuilder.build());
            return getSelectedId(result, "action");
        }

        private static String getSelectedId(
                Map<String, PromptResultItemIF> result, String itemName) {
            return ((ListResult) result.get(itemName)).getSelectedId();
        }
    }

    @Command(
            name = "install",
            aliases = {"i"},
            description =
                    "This adds the given artifacts to the list of dependencies available in the app.yml file. "
                            + "It then behaves just like 'copy --sync' and copies all artifacts in that list and all their dependencies to the target directory while at the same time removing any artifacts that are no longer needed (ie the ones that are not mentioned in the app.yml file). "
                            + "If no artifacts are passed the app.yml file will be left untouched and only the existing dependencies in the file will be copied.\n\n"
                            + "Example:\n  jpm install org.apache.httpcomponents:httpclient:4.5.14\n")
    static class Install implements Callable<Integer> {
        @Mixin QuietMixin quietMixin;
        @Mixin OptionalArtifactsMixin optionalArtifactsMixin;

        @Override
        public Integer call() throws Exception {
            SyncStats stats =
                    Jpm.builder()
                            .directory(optionalArtifactsMixin.depsMixin.directory)
                            .noLinks(optionalArtifactsMixin.depsMixin.noLinks)
                            .build()
                            .install(optionalArtifactsMixin.artifactNames);
            if (!quietMixin.quiet) {
                printStats(stats);
            }
            return (Integer) 0;
        }
    }

    @Command(
            name = "path",
            aliases = {"p"},
            description =
                    "Resolves one or more artifacts and prints the full classpath to standard output. "
                            + "If no artifacts are passed the classpath for the dependencies defined in the app.yml file will be printed instead.\n\n"
                            + "Example:\n  jpm path org.apache.httpcomponents:httpclient:4.5.14\n")
    static class PrintPath implements Callable<Integer> {
        @Mixin OptionalArtifactsMixin optionalArtifactsMixin;

        @Override
        public Integer call() throws Exception {
            List<Path> files =
                    Jpm.builder()
                            .directory(optionalArtifactsMixin.depsMixin.directory)
                            .noLinks(optionalArtifactsMixin.depsMixin.noLinks)
                            .build()
                            .path(optionalArtifactsMixin.artifactNames);
            if (!files.isEmpty()) {
                String classpath =
                        files.stream()
                                .map(Path::toString)
                                .collect(Collectors.joining(File.pathSeparator));
                System.out.print(classpath);
            }
            return (Integer) 0;
        }
    }

    @Command(
            name = "do",
            description =
                    "Executes an action command defined in the app.yml file. Actions can use variable substitution for classpath.\n\n"
                            + "Example:\n  jpm do build\n  jpm do test\n")
    static class Do implements Callable<Integer> {
        @Mixin DepsMixin depsMixin;
        @Mixin QuietMixin quietMixin;

        @Option(
                names = {"-l", "--list"},
                description = "List all available actions",
                defaultValue = "false")
        private boolean list;

        @Parameters(
                paramLabel = "action",
                description = "Name of the action to execute as defined in app.yml",
                arity = "0..*",
                index = "0")
        private String actionName;

        @Parameters(
                paramLabel = "actionsAndArguments",
                description =
                        "Optional additional actions and/or arguments to be passed to the action(s)",
                arity = "0..*",
                index = "1..*")
        private ArrayList<String> actsAndArgs = new ArrayList<>();

        @Override
        public Integer call() throws Exception {
            try {
                if (list) {
                    List<String> actionNames =
                            Jpm.builder()
                                    .directory(depsMixin.directory)
                                    .noLinks(depsMixin.noLinks)
                                    .build()
                                    .listActions();
                    if (actionNames.isEmpty()) {
                        if (!quietMixin.quiet) System.out.println("No actions defined in app.yml");
                    } else {
                        if (!quietMixin.quiet) System.out.println("Available actions:");
                        actionNames.forEach(n -> System.out.println("   " + n));
                    }
                } else {
                    if (actionName == null || actionName.isEmpty()) {
                        System.err.println(
                                "Action name is required. Use --list to see available actions.");
                        return 1;
                    }
                    // Split the full arguments list in multiple actions and their arguments
                    int idx = 0;
                    actsAndArgs.add(0, actionName);
                    while (idx < actsAndArgs.size()) {
                        String action = actsAndArgs.get(idx);
                        if (action.startsWith("-")) {
                            System.err.println(
                                    "Unexpected argument, was expecting an action name: " + action);
                            return 1;
                        }
                        idx++;
                        List<String> args = new ArrayList<>();
                        while (idx < actsAndArgs.size() && actsAndArgs.get(idx).startsWith("-")) {
                            String opt = actsAndArgs.get(idx);
                            if (opt.equals("-a") || opt.equals("--arg")) {
                                args.add(actsAndArgs.get(++idx));
                            } else if (opt.startsWith("-a=") || opt.startsWith("--arg=")) {
                                args.add(opt.substring(opt.indexOf('=') + 1));
                            } else {
                                System.err.println(
                                        "Unexpected argument, was expecting an action argument like '-a' or '--arg', not: "
                                                + opt);
                                return 1;
                            }
                            idx++;
                        }
                        int exitCode =
                                Jpm.builder()
                                        .directory(depsMixin.directory)
                                        .noLinks(depsMixin.noLinks)
                                        .verbose(!quietMixin.quiet)
                                        .build()
                                        .executeAction(action, args);
                        if (exitCode != 0) {
                            return exitCode;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
                return 1;
            }
            return 0;
        }
    }

    abstract static class DoAlias implements Callable<Integer> {
        @Mixin DepsMixin depsMixin;

        @Unmatched List<String> args = new ArrayList<>();

        abstract String actionName();

        @Override
        public Integer call() throws Exception {
            try {
                // Use only unmatched args for pass-through to preserve ordering
                return Jpm.builder()
                        .directory(depsMixin.directory)
                        .noLinks(depsMixin.noLinks)
                        .build()
                        .executeAction(actionName(), args);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                return 1;
            }
        }
    }

    @Command(
            name = "clean",
            description = "Executes the 'clean' action as defined in the app.yml file.")
    static class Clean extends DoAlias {
        @Override
        String actionName() {
            return "clean";
        }
    }

    @Command(
            name = "build",
            description = "Executes the 'build' action as defined in the app.yml file.")
    static class Build extends DoAlias {
        @Override
        String actionName() {
            return "build";
        }
    }

    @Command(
            name = "run",
            description = "Executes the 'run' action as defined in the app.yml file.")
    static class Run extends DoAlias {
        @Override
        String actionName() {
            return "run";
        }
    }

    @Command(
            name = "test",
            description = "Executes the 'test' action as defined in the app.yml file.")
    static class Test extends DoAlias {
        @Override
        String actionName() {
            return "test";
        }
    }

    static class DepsMixin {
        @Option(
                names = {"-d", "--directory"},
                description = "Directory to copy artifacts to",
                defaultValue = "deps")
        Path directory;

        @Option(
                names = {"-L", "--no-links"},
                description = "Always copy artifacts, don't try to create symlinks",
                defaultValue = "false")
        boolean noLinks;
    }

    static class ArtifactsMixin {
        @Mixin DepsMixin depsMixin;

        @Parameters(
                paramLabel = "artifacts",
                description =
                        "One or more artifacts to resolve. Artifacts have the format <group>:<artifact>[:<extension>[:<classifier>]]:<version>",
                arity = "1..*")
        private String[] artifactNames = {};
    }

    static class OptionalArtifactsMixin {
        @Mixin DepsMixin depsMixin;

        @Parameters(
                paramLabel = "artifacts",
                description =
                        "One or more artifacts to resolve. Artifacts have the format <group>:<artifact>[:<extension>[:<classifier>]]:<version>",
                arity = "0..*")
        private String[] artifactNames = {};
    }

    static class QuietMixin {
        @Option(
                names = {"-q", "--quiet"},
                description = "Don't output non-essential information",
                defaultValue = "false")
        private boolean quiet;
    }

    private static void printStats(SyncStats stats) {
        System.err.printf(
                "Artifacts new: %d, updated: %d, deleted: %d%n",
                (Integer) stats.copied, (Integer) stats.updated, (Integer) stats.deleted);
    }

    public static CommandLine getCommandLine() {
        return new CommandLine(new Main())
                .setStopAtPositional(true)
                .setAllowOptionsAsOptionParameters(true)
                .setAllowSubcommandsAsOptionParameters(true);
    }

    /**
     * Main entry point for the jpm command line tool.
     *
     * @param args The command line arguments.
     */
    public static void main(String... args) {
        getCommandLine().execute(args);
    }
}
