package org.codejive.jpm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.codejive.jpm.config.AppInfo;
import org.codejive.jpm.search.Search;
import org.codejive.jpm.util.*;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.DependencyResolutionException;

/** The class implementing all the jpm command actions. */
public class Jpm {
    private final Path directory;
    private final boolean noLinks;
    private final Path appFile;
    private final Path cacheDir;
    private final boolean verbose;

    private Jpm(Path directory, boolean noLinks, Path appFile, Path cacheDir, boolean verbose) {
        this.directory = directory;
        this.noLinks = noLinks;
        this.appFile = appFile;
        this.cacheDir = cacheDir;
        this.verbose = verbose;
    }

    /**
     * Create a new {@link Builder} instance for the {@link Jpm} class.
     *
     * @return A new {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder class for the {@link Jpm} class. */
    public static class Builder {
        private Path directory;
        private boolean noLinks;
        private Path appFile;
        private Path cacheDir;
        private boolean verbose;

        private Builder() {}

        /**
         * Set the target directory to use for the jpm commands.
         *
         * @param directory The target directory.
         * @return The builder instance for chaining.
         */
        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        /**
         * Set whether to create symbolic links or not.
         *
         * @param noLinks Whether to create symbolic links or not.
         * @return The builder instance for chaining.
         */
        public Builder noLinks(boolean noLinks) {
            this.noLinks = noLinks;
            return this;
        }

        /**
         * Set the app.yml file to use for the jpm commands.
         *
         * @param appFile The app.yml file.
         * @return The builder instance for chaining.
         */
        public Builder appFile(Path appFile) {
            this.appFile = appFile;
            return this;
        }

        /**
         * Set the cache directory to use for downloaded artifacts.
         *
         * @param cacheDir The cache directory.
         * @return The builder instance for chaining.
         */
        public Builder cacheDir(Path cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        /**
         * Set whether to enable verbose output or not.
         *
         * @param verbose Whether to enable verbose output or not.
         * @return The builder instance for chaining.
         */
        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        /**
         * Builds the {@link Jpm} instance.
         *
         * @return A {@link Jpm} instance.
         */
        public Jpm build() {
            return new Jpm(directory, noLinks, appFile, cacheDir, verbose);
        }
    }

    /**
     * Copies the given artifacts to the target directory.
     *
     * @param artifactNames The artifacts to copy.
     * @param sync Whether to sync the target directory or not.
     * @return An instance of {@link SyncResult} containing the statistics of the copy operation.
     * @throws IOException If an error occurred during the copy operation.
     * @throws DependencyResolutionException If an error occurred during the dependency resolution.
     */
    public SyncResult copy(String[] artifactNames, boolean sync)
            throws IOException, DependencyResolutionException {
        return copy(artifactNames, Collections.emptyMap(), sync);
    }

    /**
     * Copies the given artifacts to the target directory.
     *
     * @param artifactNames The artifacts to copy.
     * @param repos A map of additional repository names to URLs where artifacts can be found.
     * @param sync Whether to sync the target directory or not.
     * @return An instance of {@link SyncResult} containing the statistics of the copy operation.
     * @throws IOException If an error occurred during the copy operation.
     * @throws DependencyResolutionException If an error occurred during the dependency resolution.
     */
    public SyncResult copy(String[] artifactNames, Map<String, String> repos, boolean sync)
            throws IOException, DependencyResolutionException {
        List<Path> files = Resolver.create(artifactNames, repos, cacheDir).resolvePaths();
        return FileUtils.syncArtifacts(files, directory, noLinks, !sync);
    }

    /**
     * Searches for artifacts matching the given pattern.
     *
     * @param artifactPattern The pattern to search for.
     * @param count The maximum number of results to return.
     * @return An array of artifact names matching the given pattern.
     * @throws IOException If an error occurred during the search.
     */
    public String[] search(String artifactPattern, int count) throws IOException {
        return search(artifactPattern, count, null);
    }

    /**
     * Searches for artifacts matching the given pattern.
     *
     * @param artifactPattern The pattern to search for.
     * @param count The maximum number of results to return.
     * @return An array of artifact names matching the given pattern.
     * @throws IOException If an error occurred during the search.
     */
    public String[] search(String artifactPattern, int count, Search.Backends backend)
            throws IOException {
        List<Artifact> artifacts = new ArrayList<>();
        int max = count <= 0 || count > 200 ? 200 : count;
        Search s = Search.getBackend(backend);
        Search.SearchResult result = s.findArtifacts(artifactPattern, max);
        while (result != null) {
            artifacts.addAll(result.artifacts);
            result = count <= 0 ? s.findNextArtifacts(result) : null;
        }
        return artifacts.stream().map(Jpm::artifactGav).toArray(String[]::new);
    }

    private static String artifactGav(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    /**
     * Installs the given artifacts to the target directory while also registering them as
     * dependencies in the app.yml file in the current directory. If no artifacts are given, all
     * dependencies in the app.yml file will be installed. NB: "installation" in this context
     * basically means sync-copying the artifacts to the target directory.
     *
     * @param artifactNames The artifacts to install.
     * @return An instance of {@link SyncResult} containing the statistics of the install operation.
     * @throws IOException If an error occurred during the install operation.
     * @throws DependencyResolutionException If an error occurred during the dependency resolution.
     */
    public SyncResult install(String[] artifactNames)
            throws IOException, DependencyResolutionException {
        return install(artifactNames, Collections.emptyMap());
    }

    /**
     * Installs the given artifacts to the target directory while also registering them as
     * dependencies in the app.yml file in the current directory. If no artifacts are given, all
     * dependencies in the app.yml file will be installed. NB: "installation" in this context
     * basically means sync-copying the artifacts to the target directory.
     *
     * @param artifactNames The artifacts to install.
     * @param extraRepos A map of additional repository names to URLs where artifacts can be found.
     * @return An instance of {@link SyncResult} containing the statistics of the install operation.
     * @throws IOException If an error occurred during the install operation.
     * @throws DependencyResolutionException If an error occurred during the dependency resolution.
     */
    public SyncResult install(String[] artifactNames, Map<String, String> extraRepos)
            throws IOException, DependencyResolutionException {
        AppInfo appInfo = readAppInfo();
        String[] artifacts = getArtifacts(artifactNames, appInfo);
        Map<String, String> repos = getRepositories(extraRepos, appInfo);
        if (artifacts.length > 0) {
            List<Path> files = Resolver.create(artifacts, repos, cacheDir).resolvePaths();
            SyncResult stats = FileUtils.syncArtifacts(files, directory, noLinks, true);
            if (artifactNames.length > 0) {
                appInfo.dependencies().addAll(Arrays.asList(artifactNames));
                appInfo.repositories().putAll(repos);
                AppInfo.write(appInfo);
            }
            return stats;
        } else {
            return new SyncResult();
        }
    }

    /**
     * Returns the paths of the given artifacts. If no artifacts are given, the paths for all
     * dependencies in the app.yml file will be returned instead.
     *
     * @param artifactNames The artifacts to get the paths for.
     * @return A list of paths.
     * @throws DependencyResolutionException If an error occurred during the dependency resolution.
     * @throws IOException If an error occurred during the operation.
     */
    public List<Path> path(String[] artifactNames)
            throws DependencyResolutionException, IOException {
        return path(artifactNames, Collections.emptyMap());
    }

    /**
     * Returns the paths of the given artifacts. If no artifacts are given, the paths for all
     * dependencies in the app.yml file will be returned instead.
     *
     * @param artifactNames The artifacts to get the paths for.
     * @param extraRepos A map of additional repository names to URLs where artifacts can be found.
     * @return A list of paths.
     * @throws DependencyResolutionException If an error occurred during the dependency resolution.
     * @throws IOException If an error occurred during the operation.
     */
    public List<Path> path(String[] artifactNames, Map<String, String> extraRepos)
            throws DependencyResolutionException, IOException {
        AppInfo appInfo = readAppInfo();
        String[] deps = getArtifacts(artifactNames, appInfo);
        Map<String, String> repos = getRepositories(extraRepos, appInfo);
        if (deps.length > 0) {
            List<Path> files = Resolver.create(deps, repos, cacheDir).resolvePaths();
            if (artifactNames.length > 0) {
                return files;
            } else {
                SyncResult result = FileUtils.syncArtifacts(files, directory, noLinks, true);
                return result.files;
            }
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

    private Map<String, String> getRepositories(Map<String, String> extraRepos, AppInfo appInfo) {
        Map<String, String> repos = new HashMap<>(appInfo.repositories());
        repos.putAll(extraRepos);
        return repos;
    }

    /**
     * Executes an action defined in app.yml file.
     *
     * @param actionName The name of the action to execute
     * @return An integer containing the exit result of the action
     * @throws IllegalArgumentException If the action name is not provided or not found
     * @throws IOException If an error occurred during the operation
     * @throws DependencyResolutionException If an error occurred during dependency resolution
     * @throws InterruptedException If the action execution was interrupted
     */
    public int executeAction(String actionName, List<String> args)
            throws IOException, DependencyResolutionException, InterruptedException {
        return executeAction(actionName, args, Collections.emptyMap());
    }

    /**
     * Executes an action defined in app.yml file.
     *
     * @param actionName The name of the action to execute
     * @param args A list of additional arguments to pass to the action command
     * @param extraRepos A map of additional repository names to URLs where artifacts can be found.
     * @return An integer containing the exit result of the action
     * @throws IllegalArgumentException If the action name is not provided or not found
     * @throws IOException If an error occurred during the operation
     * @throws DependencyResolutionException If an error occurred during dependency resolution
     * @throws InterruptedException If the action execution was interrupted
     */
    public int executeAction(String actionName, List<String> args, Map<String, String> extraRepos)
            throws IOException, DependencyResolutionException, InterruptedException {
        AppInfo appInfo = readAppInfo();

        // Get the action command
        String command = appInfo.getAction(actionName);
        if (command == null) {
            throw new IllegalArgumentException(
                    "Action '"
                            + actionName
                            + "' not found in app.yml. Use --list to see available actions.");
        }

        // Add the user arguments to the command
        if (args != null && !args.isEmpty()) {
            command +=
                    args.stream()
                            .map(ScriptUtils::quoteArgument)
                            .collect(Collectors.joining(" ", " ", ""));
        }

        return executeCommand(command, extraRepos);
    }

    /**
     * Returns a list of available action names defined in the app.yml file.
     *
     * @return A list of available action names
     * @throws IOException If an error occurred during the operation
     */
    public List<String> listActions() throws IOException {
        AppInfo appInfo = readAppInfo();
        return new ArrayList<>(appInfo.getActionNames());
    }

    /**
     * Executes an action defined in app.yml file.
     *
     * @param command The command to execute
     * @return An integer containing the exit result of the action
     * @throws IOException If an error occurred during the operation
     * @throws DependencyResolutionException If an error occurred during dependency resolution
     * @throws InterruptedException If the action execution was interrupted
     */
    public int executeCommand(String command)
            throws IOException, DependencyResolutionException, InterruptedException {
        return executeCommand(command, Collections.emptyMap());
    }

    /**
     * Executes an action defined in app.yml file.
     *
     * @param command The command to execute
     * @param extraRepos A map of additional repository names to URLs where artifacts can be found.
     * @return An integer containing the exit result of the action
     * @throws IOException If an error occurred during the operation
     * @throws DependencyResolutionException If an error occurred during dependency resolution
     * @throws InterruptedException If the action execution was interrupted
     */
    public int executeCommand(String command, Map<String, String> extraRepos)
            throws IOException, DependencyResolutionException, InterruptedException {
        // Get the classpath for variable substitution only if needed
        List<Path> classpath = Collections.emptyList();
        if (command.contains("{{deps}}")) {
            classpath =
                    this.path(
                            new String[0],
                            extraRepos); // Empty array means use dependencies from app.yml
        }

        return ScriptUtils.executeScript(command, classpath, verbose);
    }

    private AppInfo readAppInfo() throws IOException {
        return (appFile != null) ? AppInfo.read(appFile) : AppInfo.read();
    }
}
