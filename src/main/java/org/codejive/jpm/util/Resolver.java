package org.codejive.jpm.util;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

public class Resolver {
    private final List<Artifact> artifacts;
    private final List<RemoteRepository> repositories;

    private List<ArtifactResult> resolvedArtifacts;

    public static Resolver create(String[] artifactNames, Map<String, String> repositories) {
        return new Resolver(artifactNames, repositories);
    }

    private Resolver(String[] artifactNames, Map<String, String> repos) {
        artifacts = parseArtifacts(artifactNames);
        repositories = parseRepositories(repos);
    }

    public List<ArtifactResult> resolve() throws DependencyResolutionException {
        if (resolvedArtifacts == null) {
            resolvedArtifacts = resolveArtifacts(artifacts, repositories);
        }
        return resolvedArtifacts;
    }

    public List<Path> resolvePaths() throws DependencyResolutionException {
        List<ArtifactResult> ras = resolve();
        return ras.stream()
                .map(ar -> ar.getArtifact().getFile().toPath())
                .collect(Collectors.toList());
    }

    /**
     * Resolves the given artifacts.
     *
     * @param artifacts the artifacts to resolve as a list of {@link Artifact} instances
     * @return the resolved artifacts as a list of {@link ArtifactResult} instances
     * @throws DependencyResolutionException if an error occurs while resolving the artifacts
     */
    public static List<ArtifactResult> resolveArtifacts(
            List<Artifact> artifacts, List<RemoteRepository> repositories)
            throws DependencyResolutionException {
        List<Dependency> dependencies =
                artifacts.stream()
                        .map(a -> new Dependency(a, JavaScopes.RUNTIME))
                        .collect(Collectors.toList());
        ContextOverrides overrides =
                ContextOverrides.create().withUserSettings(true).repositories(repositories).build();
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

    private static List<Artifact> parseArtifacts(String[] artifactNames) {
        return Arrays.stream(artifactNames).map(DefaultArtifact::new).collect(Collectors.toList());
    }

    private static List<RemoteRepository> parseRepositories(Map<String, String> repositories) {
        if (repositories == null) {
            return Collections.emptyList();
        }
        return repositories.entrySet().stream()
                .map(e -> new RemoteRepository.Builder(e.getKey(), "default", e.getValue()).build())
                .collect(Collectors.toList());
    }
}
