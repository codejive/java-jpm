// spotless:off Dependencies for JBang
//DEPS eu.maveniverse.maven.mima:context:2.4.15 eu.maveniverse.maven.mima.runtime:standalone-static:2.4.15
//DEPS info.picocli:picocli:4.7.6
//DEPS com.google.code.gson:gson:2.11.0
//DEPS org.slf4j:slf4j-api:2.0.13 org.slf4j:slf4j-simple:2.0.13
//SOURCES Jpm.java json/JpmProject.java util/FileUtils.java util/ResolverUtils.java util/SyncStats.java
// spotless:on

package org.codejive.jpm;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.codejive.jpm.util.SyncStats;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "jpm",
        mixinStandardHelpOptions = true,
        version = "jpm 0.1",
        description = "Simple command line tool for managing Maven artifacts",
        subcommands = {Main.Copy.class, Main.Sync.class, Main.Install.class, Main.PrintPath.class})
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

        @Override
        public Integer call() throws Exception {
            SyncStats stats =
                    Jpm.builder()
                            .directory(artifactsMixin.copyMixin.directory)
                            .noLinks(artifactsMixin.copyMixin.noLinks)
                            .build()
                            .copy(artifactsMixin.artifactNames);
            if (!quietMixin.quiet) {
                printStats(stats);
            }
            return 0;
        }
    }

    @Command(
            name = "sync",
            aliases = {"s"},
            description =
                    "Resolves one or more artifacts and copies them and all their dependencies to a target directory while at the same time removing any artifacts that are no longer needed (ie the ones that are not mentioned when running this command). "
                            + "By default jpm will try to create symbolic links to conserve space.\n\n"
                            + "Example:\n  jpm sync org.apache.httpcomponents:httpclient:4.5.14\n")
    static class Sync implements Callable<Integer> {
        @Mixin QuietMixin quietMixin;
        @Mixin ArtifactsMixin artifactsMixin;

        @Override
        public Integer call() throws Exception {
            SyncStats stats =
                    Jpm.builder()
                            .directory(artifactsMixin.copyMixin.directory)
                            .noLinks(artifactsMixin.copyMixin.noLinks)
                            .build()
                            .sync(artifactsMixin.artifactNames);
            if (!quietMixin.quiet) {
                printStats(stats);
            }
            return 0;
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
            return 0;
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
            return 0;
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
        System.err.printf("Artifacts copied: %d, deleted: %d%n", stats.copied, stats.deleted);
    }

    public static void main(String... args) {
        new CommandLine(new Main()).execute(args);
    }
}
