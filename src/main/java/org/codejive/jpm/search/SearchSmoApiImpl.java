package org.codejive.jpm.search;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.search.api.MAVEN;
import org.apache.maven.search.api.Record;
import org.apache.maven.search.api.SearchRequest;
import org.apache.maven.search.api.request.BooleanQuery;
import org.apache.maven.search.api.request.FieldQuery;
import org.apache.maven.search.api.request.Paging;
import org.apache.maven.search.api.request.Query;
import org.apache.maven.search.backend.smo.SmoSearchBackend;
import org.apache.maven.search.backend.smo.SmoSearchBackendFactory;
import org.apache.maven.search.backend.smo.SmoSearchResponse;
import org.eclipse.aether.artifact.DefaultArtifact;

public class SearchSmoApiImpl implements Search {
    private final SmoSearchBackend backend;

    public static Search createSmo() {
        return new SearchSmoApiImpl(SmoSearchBackendFactory.createSmo());
    }

    public static Search createCsc() {
        return new SearchSmoApiImpl(SmoSearchBackendFactory.createCsc());
    }

    private SearchSmoApiImpl(SmoSearchBackend backend) {
        this.backend = backend;
    }

    @Override
    public SearchResult findArtifacts(String query, int count) throws IOException {
        return select(query, 0, count);
    }

    @Override
    public SearchResult findNextArtifacts(SearchResult prevResult) throws IOException {
        return select(prevResult.query, prevResult.start + 1, prevResult.count);
    }

    private SearchResult select(String query, int start, int count) throws IOException {
        String[] parts = query.split(":", -1);
        Query q;
        if (parts.length >= 3) {
            // Exact group/artifact match for retrieving versions
            q =
                    BooleanQuery.and(
                            FieldQuery.fieldQuery(MAVEN.GROUP_ID, parts[0]),
                            FieldQuery.fieldQuery(MAVEN.ARTIFACT_ID, parts[1]));
        } else if (parts.length == 2) {
            // Partial group/artifact match, we will filter the results
            // to remove those that match an inverted artifact/group
            q = Query.query(String.format("%s %s", parts[0], parts[1]));
        } else {
            // Simple partial match
            q = Query.query(query);
        }
        SearchRequest req = new SearchRequest(new Paging(count, start), q);
        SmoSearchResponse res = backend.search(req);
        List<DefaultArtifact> artifacts =
                res.getPage().stream()
                        .filter(r -> acceptRecord(r, parts))
                        .map(SearchSmoApiImpl::toArtifact)
                        .collect(Collectors.toList());
        return new SearchResult(
                artifacts,
                query,
                req.getPaging().getPageOffset(),
                res.getCurrentHits(),
                res.getTotalHits());
    }

    private static boolean acceptRecord(Record r, String[] parts) {
        String grp = r.getValue(MAVEN.GROUP_ID);
        String art = r.getValue(MAVEN.ARTIFACT_ID);
        String pkg = r.getValue(MAVEN.PACKAGING);
        return pkg != null
                && grp != null
                && art != null
                && (pkg.equals("jar") || pkg.equals("bundle"))
                && (parts.length != 2 || (grp.contains(parts[0]) && art.contains(parts[1])));
    }

    private static DefaultArtifact toArtifact(Record r) {
        String grp = r.getValue(MAVEN.GROUP_ID);
        String art = r.getValue(MAVEN.ARTIFACT_ID);
        String ver = r.getValue(MAVEN.VERSION);
        return new DefaultArtifact(grp, art, "", ver);
    }
}
