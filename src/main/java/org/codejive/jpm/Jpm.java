package org.codejive.jpm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.codejive.jpm.json.AppInfo;
import org.codejive.jpm.util.FileUtils;
import org.codejive.jpm.util.ResolverUtils;
import org.codejive.jpm.util.SearchUtils;
import org.codejive.jpm.util.SyncStats;
import org.eclipse.aether.artifact.Artifact;
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

    public SyncStats copy(String[] artifactNames, boolean sync)
            throws IOException, DependencyResolutionException {
        List<Path> files = ResolverUtils.resolveArtifactPaths(artifactNames);
        return FileUtils.syncArtifacts(files, directory, noLinks, !sync);
    }

    public String[] search(String artifactPattern, int count) throws IOException {
        List<Artifact> artifacts = new ArrayList<>();
        int max = count <= 0 || count > 200 ? 200 : count;
        SearchUtils.SearchResult result = SearchUtils.findArtifacts(artifactPattern, max);
        while (result != null) {
            artifacts.addAll(result.artifacts);
            result = count <= 0 ? SearchUtils.findNextArtifacts(result) : null;
        }
        return artifacts.stream().map(Jpm::artifactGav).toArray(String[]::new);
    }

    private static String artifactGav(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    public SyncStats install(String[] artifactNames)
            throws IOException, DependencyResolutionException {
        AppInfo appInfo = AppInfo.read();
        String[] artifacts = getArtifacts(artifactNames, appInfo);
        if (artifacts.length > 0) {
            List<Path> files = ResolverUtils.resolveArtifactPaths(artifacts);
            SyncStats stats = FileUtils.syncArtifacts(files, directory, noLinks, true);
            if (artifactNames.length > 0) {
                for (String dep : artifactNames) {
                    int p = dep.lastIndexOf(':');
                    String name = dep.substring(0, p);
                    String version = dep.substring(p + 1);
                    appInfo.dependencies.put(name, version);
                }
                AppInfo.write(appInfo);
            }
            return stats;
        } else {
            return new SyncStats();
        }
    }

    public List<Path> path(String[] artifactNames)
            throws DependencyResolutionException, IOException {
        AppInfo appInfo = AppInfo.read();
        String[] deps = getArtifacts(artifactNames, appInfo);
        if (deps.length > 0) {
            return ResolverUtils.resolveArtifactPaths(deps);
        } else {
            return Collections.emptyList();
        }
    }

    private static String[] getArtifacts(String[] artifactNames, AppInfo appInfo) {
        String[] deps;
        if (artifactNames.length > 0) {
            deps = artifactNames;
        } else {
            deps = appInfo.getDependencyGAVs();
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
