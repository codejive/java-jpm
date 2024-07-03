package org.codejive.jpm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.codejive.jpm.json.JpmProject;
import org.codejive.jpm.util.FileUtils;
import org.codejive.jpm.util.ResolverUtils;
import org.codejive.jpm.util.SyncStats;
import org.eclipse.aether.resolution.DependencyResolutionException;

public class Jpm {
    private final Path directory;
    private final boolean noLinks;

    private Jpm(Path directory, boolean noLinks) {
        this.directory = directory;
        this.noLinks = noLinks;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path directory;
        private boolean noLinks;

        private Builder() {}

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public Builder noLinks(boolean noLinks) {
            this.noLinks = noLinks;
            return this;
        }

        public Jpm build() {
            return new Jpm(directory, noLinks);
        }
    }

    public SyncStats copy(String[] artifactNames)
            throws IOException, DependencyResolutionException {
        List<Path> files = ResolverUtils.resolveArtifactPaths(artifactNames);
        return FileUtils.syncArtifacts(files, directory, noLinks, true);
    }

    public SyncStats sync(String[] artifactNames)
            throws IOException, DependencyResolutionException {
        List<Path> files = ResolverUtils.resolveArtifactPaths(artifactNames);
        return FileUtils.syncArtifacts(files, directory, noLinks, false);
    }

    public SyncStats install(String[] artifactNames)
            throws IOException, DependencyResolutionException {
        JpmProject prj = JpmProject.read();
        String[] artifacts = getArtifacts(artifactNames, prj);
        if (artifacts.length > 0) {
            List<Path> files = ResolverUtils.resolveArtifactPaths(artifacts);
            SyncStats stats = FileUtils.syncArtifacts(files, directory, noLinks, true);
            if (artifactNames.length > 0) {
                for (String dep : artifactNames) {
                    int p = dep.lastIndexOf(':');
                    String name = dep.substring(0, p);
                    String version = dep.substring(p + 1);
                    prj.dependencies.put(name, version);
                }
                JpmProject.write(prj);
            }
            return stats;
        } else {
            return new SyncStats();
        }
    }

    public List<Path> path(String[] artifactNames)
            throws DependencyResolutionException, IOException {
        JpmProject prj = JpmProject.read();
        String[] deps = getArtifacts(artifactNames, prj);
        if (deps.length > 0) {
            return ResolverUtils.resolveArtifactPaths(deps);
        } else {
            return Collections.emptyList();
        }
    }

    public int run(String name, String[] args) throws IOException, InterruptedException {
        String cmdString = JpmProject.read().commands.get(name);
        if (cmdString == null) {
            throw new IllegalArgumentException("Command not found: " + name);
        }
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

    private static boolean isWindows() {
        String os =
                System.getProperty("os.name")
                        .toLowerCase(Locale.ENGLISH)
                        .replaceAll("[^a-z0-9]+", "");
        return os.startsWith("win");
    }
}
