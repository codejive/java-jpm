// spotless:off Dependencies for JBang
//DEPS eu.maveniverse.maven.mima:context:2.4.15 eu.maveniverse.maven.mima.runtime:standalone-static:2.4.15
//DEPS info.picocli:picocli:4.7.6
//DEPS com.google.code.gson:gson:2.11.0
//DEPS org.slf4j:slf4j-api:2.0.13 org.slf4j:slf4j-simple:2.0.13
// spotless:on

package org.codejive.jpm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
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
        subcommands = {
            Main.Copy.class,
            Main.Sync.class,
            Main.Install.class,
            Main.PrintPath.class,
            Main.Run.class
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

        @Override
        public Integer call() throws Exception {
            List<Path> files = resolveArtifactPaths(artifactsMixin.artifactNames);
            SyncStats stats =
                    syncArtifacts(
                            files,
                            artifactsMixin.copyMixin.directory,
                            artifactsMixin.copyMixin.noLinks,
                            true);
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
            List<Path> files = resolveArtifactPaths(artifactsMixin.artifactNames);
            SyncStats stats =
                    syncArtifacts(
                            files,
                            artifactsMixin.copyMixin.directory,
                            artifactsMixin.copyMixin.noLinks,
                            false);
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
                    "This adds the given artifacts to the list of dependencies available in the jpm.json file. "
                            + "It then behaves just like 'sync' and copies all artifacts in that list and all their dependencies to the target directory while at the same time removing any artifacts that are no longer needed (ie the ones that are not mentioned in the jpm.json file)."
                            + "If no artifacts are passed the jpm.json file will be left untouched and only the existing dependencies in the file will be copied.\n\n"
                            + "Example:\n  jpm install org.apache.httpcomponents:httpclient:4.5.14\n")
    static class Install implements Callable<Integer> {
        @Mixin QuietMixin quietMixin;
        @Mixin OptionalArtifactsMixin optionalArtifactsMixin;

        @Override
        public Integer call() throws Exception {
            JpmProject prj = readProjectJson();
            String[] artifacts = getArtifacts(optionalArtifactsMixin.artifactNames, prj);
            if (artifacts.length > 0) {
                List<Path> files = resolveArtifactPaths(artifacts);
                SyncStats stats =
                        syncArtifacts(
                                files,
                                optionalArtifactsMixin.copyMixin.directory,
                                optionalArtifactsMixin.copyMixin.noLinks,
                                true);
                if (optionalArtifactsMixin.artifactNames.length > 0) {
                    for (String dep : optionalArtifactsMixin.artifactNames) {
                        int p = dep.lastIndexOf(':');
                        String name = dep.substring(0, p);
                        String version = dep.substring(p + 1);
                        prj.dependencies.put(name, version);
                    }
                    writeProjectJson(prj);
                }
                if (!quietMixin.quiet) {
                    printStats(stats);
                }
            }
            return 0;
        }
    }

    @Command(
            name = "path",
            aliases = {"p"},
            description =
                    "Resolves one or more artifacts and prints the full classpath to standard output. "
                            + "If no artifacts are passed the classpath for the dependencies defined in the jpm.json file will be printed instead.\n\n"
                            + "Example:\n  jpm path org.apache.httpcomponents:httpclient:4.5.14\n")
    static class PrintPath implements Callable<Integer> {
        @Mixin OptionalArtifactsMixin optionalArtifactsMixin;

        @Override
        public Integer call() throws Exception {
            JpmProject prj = readProjectJson();
            String[] deps = getArtifacts(optionalArtifactsMixin.artifactNames, prj);
            if (deps.length > 0) {
                List<Path> files = resolveArtifactPaths(deps);
                String classpath =
                        files.stream()
                                .map(Path::toString)
                                .collect(Collectors.joining(File.pathSeparator));
                System.out.print(classpath);
            }
            return 0;
        }
    }

    private static String[] getArtifacts(String[] artifactNames, JpmProject prj) {
        String[] deps;
        if (artifactNames.length > 0) {
            deps = artifactNames;
        } else {
            deps =
                    prj.dependencies.entrySet().stream()
                            .map(e -> e.getKey() + ":" + e.getValue())
                            .toArray(String[]::new);
        }
        return deps;
    }

    @Command(
            name = "run",
            aliases = {"r"},
            description =
                    "Run given command defined in the jpm.json project file\n\n"
                            + "Example:\n  jpm run run-me\n")
    static class Run implements Callable<Integer> {
        @Parameters(
                paramLabel = "name",
                description =
                        "The name of the command to run. Commands are defined in the jpm.json project file",
                index = "0",
                arity = "0..1")
        private String name;

        @Parameters(
                paramLabel = "arguments",
                description = "Optional list of additional arguments",
                index = "1..*",
                arity = "0..*")
        private String[] args = {};

        @Override
        public Integer call() throws Exception {
            if (name == null) {
                Map<String, String> cmds = readProjectJson().commands;
                if (cmds.isEmpty()) {
                    System.err.println("No commands defined in the jpm.json project file");
                } else {
                    System.err.println("Available commands:");
                    for (String cmd : cmds.keySet()) {
                        System.err.println("  " + cmd);
                        System.err.println("    " + cmds.get(cmd));
                    }
                }
                return 0;
            }
            String cmdString = readProjectJson().commands.get(name);
            if (cmdString != null) {
                String[] quotedArgs =
                        Arrays.stream(args)
                                .map(a -> a.contains(" ") ? "\"" + a + "\"" : a)
                                .toArray(String[]::new);
                String extraArgs = String.join(" ", quotedArgs);
                String[] cmd;
                if (isWindows()) {
                    cmd = new String[] {"cmd", "/c", String.join(" ", cmdString, extraArgs)};
                } else {
                    cmd = new String[] {"/bin/sh", "-c", String.join(" ", cmdString, extraArgs)};
                }
                return new ProcessBuilder(cmd).inheritIO().start().waitFor();
            } else {
                System.err.printf(
                        "No command with the name '%s' exists in the jpm.json project file%n",
                        name);
                return 1;
            }
        }
    }

    private static List<Path> resolveArtifactPaths(String[] artifactNames)
            throws DependencyResolutionException {
        List<Artifact> artifacts = parseArtifacts(artifactNames);
        List<ArtifactResult> resolvedArtifacts = resolveArtifacts(artifacts);
        return resolvedArtifacts.stream().map(ar -> ar.getArtifact().getFile().toPath()).toList();
    }

    private static List<Artifact> parseArtifacts(String[] artifactNames) {
        return Arrays.stream(artifactNames).map(DefaultArtifact::new).collect(Collectors.toList());
    }

    private static List<ArtifactResult> resolveArtifacts(List<Artifact> artifacts)
            throws DependencyResolutionException {
        List<Dependency> dependencies =
                artifacts.stream().map(a -> new Dependency(a, JavaScopes.RUNTIME)).toList();
        ContextOverrides overrides = ContextOverrides.create().build();
        Runtime runtime = Runtimes.INSTANCE.getRuntime();
        try (Context context = runtime.create(overrides)) {
            CollectRequest collectRequest =
                    new CollectRequest()
                            .setDependencies(dependencies)
                            .setRepositories(context.remoteRepositories());
            DependencyRequest dependencyRequest =
                    new DependencyRequest().setCollectRequest(collectRequest);

            DependencyResult dependencyResult =
                    context.repositorySystem()
                            .resolveDependencies(
                                    context.repositorySystemSession(), dependencyRequest);
            return dependencyResult.getArtifactResults();
        }
    }

    private static class SyncStats {
        int copied;
        int updated;
        int deleted;
    }

    private static SyncStats syncArtifacts(
            List<Path> artifacts, Path directory, boolean noLinks, boolean noDelete)
            throws IOException {
        SyncStats stats = new SyncStats();

        // Make sure the target directory exists
        Files.createDirectories(directory);

        // Remember current artifact names in target directory (if any)
        Set<String> artifactsToDelete = new HashSet<>();
        if (!noDelete) {
            File[] files = directory.toFile().listFiles(File::isFile);
            if (files != null) {
                for (File file : files) {
                    artifactsToDelete.add(file.getName());
                }
            }
        }

        // Copy artifacts
        for (Path artifact : artifacts) {
            String artifactName = artifact.getFileName().toString();
            Path target = directory.resolve(artifactName);
            if (!Files.exists(target)) {
                copyDependency(artifact, directory, noLinks);
                artifactsToDelete.remove(artifactName);
                stats.copied++;
            }
        }

        // Now remove any artifacts that are no longer needed
        if (!noDelete) {
            for (String existingArtifact : artifactsToDelete) {
                Path target = directory.resolve(existingArtifact);
                Files.delete(target);
                stats.deleted++;
            }
        }

        return stats;
    }

    private static void copyDependency(Path artifact, Path directory, boolean noLinks)
            throws IOException {
        Path target = directory.resolve(artifact.getFileName().toString());
        if (!noLinks) {
            Files.deleteIfExists(target);
            try {
                Files.createSymbolicLink(target, artifact);
                return;
            } catch (IOException e) {
                // Creating a symlink might fail (eg on Windows) so we
                // fall through and try again by simply copying the file
            }
        }
        Files.copy(artifact, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static class JpmProject {
        Map<String, String> dependencies;
        Map<String, String> commands;
    }

    private static JpmProject readProjectJson() throws IOException {
        Path prjJson = Path.of("jpm.json");
        JpmProject prj;
        if (Files.isRegularFile(prjJson)) {
            try (Reader in = Files.newBufferedReader(prjJson)) {
                Gson parser = new GsonBuilder().create();
                prj = parser.fromJson(in, JpmProject.class);
            }
        } else {
            prj = new JpmProject();
        }
        if (prj.dependencies == null) {
            prj.dependencies = new TreeMap<>();
        } else {
            prj.dependencies = new TreeMap<>(prj.dependencies);
        }
        if (prj.commands == null) {
            prj.commands = new TreeMap<>();
        } else {
            prj.commands = new TreeMap<>(prj.commands);
        }
        return prj;
    }

    private static void writeProjectJson(JpmProject prj) throws IOException {
        Path prjJson = Path.of("jpm.json");
        try (Writer out = Files.newBufferedWriter(prjJson)) {
            Gson parser = new GsonBuilder().setPrettyPrinting().create();
            parser.toJson(prj, out);
        }
    }

    private static void printStats(SyncStats stats) {
        System.err.printf(
                "Artifacts copied: %d, deleted: %d%n",
                stats.copied, stats.deleted);
    }

    private static boolean isWindows() {
        String os =
                System.getProperty("os.name")
                        .toLowerCase(Locale.ENGLISH)
                        .replaceAll("[^a-z0-9]+", "");
        return os.startsWith("win");
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

    public static void main(String... args) {
        new CommandLine(new Main()).execute(args);
    }
}
