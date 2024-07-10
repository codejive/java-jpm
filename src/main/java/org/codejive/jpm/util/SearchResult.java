package org.codejive.jpm.util;

import java.util.Collections;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;

/** Hold the result of a search while also functioning as a kind of bookmark for paging purposes. */
public class SearchResult {
    /** The artifacts that matched the search query. */
    public final List<? extends Artifact> artifacts;

    /** The search query that produced this result. */
    public final String query;

    /** The index of the first artifact in this result relative to the total result set. */
    public final int start;

    /** The maximum number of results to return */
    public final int count;

    /** The total number of artifacts that matched the search query. */
    public final int total;

    /**
     * Create a new search result.
     *
     * @param artifacts The artifacts that matched the search query.
     * @param query The search query that produced this result.
     * @param start The index of the first artifact in this result relative to the total result set.
     * @param count The maximum number of results to return.
     * @param total The total number of artifacts that matched the search query.
     */
    public SearchResult(
            List<? extends Artifact> artifacts, String query, int start, int count, int total) {
        this.artifacts = Collections.unmodifiableList(artifacts);
        this.query = query;
        this.start = start;
        this.count = count;
        this.total = total;
    }
}
