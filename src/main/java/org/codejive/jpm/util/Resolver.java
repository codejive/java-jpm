package org.codejive.jpm.util;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Resolver {
    private final List<Artifact> artifacts;

    private List<ArtifactResult> resolvedArtifacts;

    public static Resolver create(String[] artifactNames) {
        return new Resolver(artifactNames);
    }

    private Resolver(String[] artifactNames) {
        artifacts = parseArtifacts(artifactNames);
    }

    public List<ArtifactResult> resolve() throws DependencyResolutionException {
        if (resolvedArtifacts == null) {
            resolvedArtifacts = resolveArtifacts(artifacts);
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
     * Parses the given artifact names into a list of {@link Artifact} instances.
     *
     * @param artifactNames the artifact names to parse as an array of strings in the format
     *     "groupId:artifactId:version"
     * @return a list of {@link Artifact} instances
     */
    public static List<Artifact> parseArtifacts(String[] artifactNames) {
        return Arrays.stream(artifactNames).map(DefaultArtifact::new).collect(Collectors.toList());
    }

    /**
     * Resolves the given artifacts.
     *
     * @param artifacts the artifacts to resolve as a list of {@link Artifact} instances
     * @return the resolved artifacts as a list of {@link ArtifactResult} instances
     * @throws DependencyResolutionException if an error occurs while resolving the artifacts
     */
    public static List<ArtifactResult> resolveArtifacts(List<Artifact> artifacts)
            throws DependencyResolutionException {
        List<Dependency> dependencies =
                artifacts.stream()
                        .map(a -> new Dependency(a, JavaScopes.RUNTIME))
                        .collect(Collectors.toList());
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
}
