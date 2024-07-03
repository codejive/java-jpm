package org.codejive.jpm.util;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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

public class ResolverUtils {
    public static List<Path> resolveArtifactPaths(String[] artifactNames)
            throws DependencyResolutionException {
        List<Artifact> artifacts = parseArtifacts(artifactNames);
        List<ArtifactResult> resolvedArtifacts = resolveArtifacts(artifacts);
        return resolvedArtifacts.stream().map(ar -> ar.getArtifact().getFile().toPath()).toList();
    }

    public static List<Artifact> parseArtifacts(String[] artifactNames) {
        return Arrays.stream(artifactNames).map(DefaultArtifact::new).collect(Collectors.toList());
    }

    public static List<ArtifactResult> resolveArtifacts(List<Artifact> artifacts)
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
}
