// spotless:off Dependencies for JBang
//DEPS eu.maveniverse.maven.mima:context:2.4.15 eu.maveniverse.maven.mima.runtime:standalone-static:2.4.15
//DEPS info.picocli:picocli:4.7.6
//DEPS com.google.code.gson:gson:2.11.0
//DEPS org.jline:jline-console-ui:3.26.2 org.jline:jline-terminal-jni:3.26.2
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
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.ListResult;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.ListPromptBuilder;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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
            Main.PrintPath.class
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
                            .directory(artifactsMixin.copyMixin.directory)
                            .noLinks(artifactsMixin.copyMixin.noLinks)
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
                    "Finds and returns the names of those artifacts that match the given (partial) name.\n\n"
                            + "Example:\n  jpm search httpclient\n")
    static class Search implements Callable<Integer> {
        @Mixin QuietMixin quietMixin;
        @Mixin CopyMixin copyMixin;

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
                        ConsolePrompt prompt = new ConsolePrompt(terminal);
                        if (artifactPattern == null || artifactPattern.isEmpty()) {
                            artifactPattern = askString(prompt, "Search for:");
                        }
                        String[] artifactNames = search(artifactPattern);
                        PromptBuilder promptBuilder = prompt.getPromptBuilder();
                        addSelectItem(promptBuilder, "Select artifact:", artifactNames);
                        addSelectArtifactAction(promptBuilder);
                        Map<String, PromptResultItemIF> result =
                                prompt.prompt(promptBuilder.build());
                        String selectedArtifact = getSelectedId(result, "item");
                        String artifactAction = getSelectedId(result, "action");
                        if ("install".equals(artifactAction)) {
                            SyncStats stats =
                                    Jpm.builder()
                                            .directory(copyMixin.directory)
                                            .noLinks(copyMixin.noLinks)
                                            .build()
                                            .install(new String[] {selectedArtifact});
                            if (!quietMixin.quiet) {
                                printStats(stats);
                            }
                        } else if ("copy".equals(artifactAction)) {
                            SyncStats stats =
                                    Jpm.builder()
                                            .directory(copyMixin.directory)
                                            .noLinks(copyMixin.noLinks)
                                            .build()
                                            .copy(new String[] {selectedArtifact}, false);
                            if (!quietMixin.quiet) {
                                printStats(stats);
                            }
                        } else if ("version".equals(artifactAction)) {
                            artifactPattern = selectedArtifact;
                            continue;
                        } else { // quit
                            break;
                        }
                        String finalAction = selectFinalAction(prompt);
                        if ("quit".equals(finalAction)) {
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

        String[] search(String artifactPattern) throws IOException {
            return Jpm.builder()
                    .directory(copyMixin.directory)
                    .noLinks(copyMixin.noLinks)
                    .build()
                    .search(artifactPattern, Math.min(max, 200));
        }

        String askString(ConsolePrompt prompt, String message) throws IOException {
            PromptBuilder promptBuilder = prompt.getPromptBuilder();
            promptBuilder.createInputPrompt().name("input").message(message).addPrompt();
            Map<String, PromptResultItemIF> result = prompt.prompt(promptBuilder.build());
            return result.get("input").getResult();
        }

        void addSelectItem(PromptBuilder promptBuilder, String message, String[] items)
                throws IOException {
            ListPromptBuilder artifactsList =
                    promptBuilder.createListPrompt().name("item").message(message).pageSize(10);
            for (String artifactName : items) {
                artifactsList.newItem(artifactName).text(artifactName).add();
            }
            artifactsList.addPrompt();
        }

        void addSelectArtifactAction(PromptBuilder promptBuilder) throws IOException {
            promptBuilder
                    .createListPrompt()
                    .name("action")
                    .message("What to do:")
                    .newItem("install")
                    .text("Download & Install artifact")
                    .add()
                    .newItem("copy")
                    .text("Download & Copy artifact")
                    .add()
                    .newItem("version")
                    .text("Select different version")
                    .add()
                    .newItem("quit")
                    .text("Quit")
                    .add()
                    .addPrompt();
        }

        String selectFinalAction(ConsolePrompt prompt) throws IOException {
            PromptBuilder promptBuilder = prompt.getPromptBuilder();
            promptBuilder
                    .createListPrompt()
                    .name("action")
                    .message("What to do:")
                    .newItem("again")
                    .text("Search again")
                    .add()
                    .newItem("quit")
                    .text("Quit")
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
                    "This adds the given artifacts to the list of dependencies available in the app.json file. "
                            + "It then behaves just like 'sync' and copies all artifacts in that list and all their dependencies to the target directory while at the same time removing any artifacts that are no longer needed (ie the ones that are not mentioned in the app.json file)."
                            + "If no artifacts are passed the app.json file will be left untouched and only the existing dependencies in the file will be copied.\n\n"
                            + "Example:\n  jpm install org.apache.httpcomponents:httpclient:4.5.14\n")
    static class Install implements Callable<Integer> {
        @Mixin QuietMixin quietMixin;
        @Mixin OptionalArtifactsMixin optionalArtifactsMixin;

        @Override
        public Integer call() throws Exception {
            SyncStats stats =
                    Jpm.builder()
                            .directory(optionalArtifactsMixin.copyMixin.directory)
                            .noLinks(optionalArtifactsMixin.copyMixin.noLinks)
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
                            + "If no artifacts are passed the classpath for the dependencies defined in the app.json file will be printed instead.\n\n"
                            + "Example:\n  jpm path org.apache.httpcomponents:httpclient:4.5.14\n")
    static class PrintPath implements Callable<Integer> {
        @Mixin OptionalArtifactsMixin optionalArtifactsMixin;

        @Override
        public Integer call() throws Exception {
            List<Path> files =
                    Jpm.builder()
                            .directory(optionalArtifactsMixin.copyMixin.directory)
                            .noLinks(optionalArtifactsMixin.copyMixin.noLinks)
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

    static class CopyMixin {
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
        @Mixin CopyMixin copyMixin;

        @Parameters(
                paramLabel = "artifacts",
                description =
                        "One or more artifacts to resolve. Artifacts have the format <group>:<artifact>[:<extension>[:<classifier>]]:<version>",
                arity = "1..*")
        private String[] artifactNames = {};
    }

    static class OptionalArtifactsMixin {
        @Mixin CopyMixin copyMixin;

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

    /**
     * Main entry point for the jpm command line tool.
     *
     * @param args The command line arguments.
     */
    public static void main(String... args) {
        if (args.length == 0) {
            System.err.println(
                    "Running 'jpm search --interactive', try 'jpm --help' for more options");
            args = new String[] {"search", "--interactive"};
        }
        new CommandLine(new Main()).execute(args);
    }
}
