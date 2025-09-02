package org.codejive.jpm.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Utility class for keeping track of synchronization statistics. */
public class SyncResult {
    public List<Path> files = new ArrayList<>();

    /** The number of new artifacts that were copied. */
    public int copied;

    /** The number of existing artifacts that were updated. */
    public int updated;

    /** The number of existing artifacts that were deleted. */
    public int deleted;
}
