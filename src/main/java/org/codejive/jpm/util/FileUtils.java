package org.codejive.jpm.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utility class for file operations. */
public class FileUtils {

    /**
     * Synchronizes a list of artifacts with a target directory.
     *
     * @param artifacts list of artifacts to synchronize
     * @param directory target directory
     * @param noLinks if true, copy artifacts instead of creating symbolic links
     * @param noDelete if true, do not delete artifacts that are no longer needed
     * @return An instance of {@link SyncResult} with statistics about the synchronization
     * @throws IOException if an error occurred during the synchronization
     */
    public static SyncResult syncArtifacts(
            List<Path> artifacts, Path directory, boolean noLinks, boolean noDelete)
            throws IOException {
        SyncResult stats = new SyncResult();

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
            artifactsToDelete.remove(artifactName);
            Path target = directory.resolve(artifactName);
            stats.files.add(target);
            if (!Files.exists(target)) {
                copyDependency(artifact, directory, noLinks);
                stats.copied++;
            } else if (Files.isSymbolicLink(target) == noLinks) {
                copyDependency(artifact, directory, noLinks);
                stats.updated++;
            } else if (Files.size(target) != Files.size(artifact)
                    || !Files.getLastModifiedTime(target)
                            .equals(Files.getLastModifiedTime(artifact))) {
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
        Files.copy(
                artifact,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static Path safePath(String w) {
        try {
            return Paths.get(w);
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
