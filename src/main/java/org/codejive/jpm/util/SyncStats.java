package org.codejive.jpm.util;

/** Utility class for keeping track of synchronization statistics. */
public class SyncStats {
    /** The number of new artifacts that were copied. */
    public int copied;

    /** The number of existing artifacts that were updated. */
    public int updated;

    /** The number of existing artifacts that were deleted. */
    public int deleted;
}
