package org.codejive.jpm.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileUtils {
    public static SyncStats syncArtifacts(
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
            } else if (Files.isSymbolicLink(target) == noLinks) {
                copyDependency(artifact, directory, noLinks);
                stats.updated++;
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
}
