package org.codejive.jpm.util;

import picocli.CommandLine;

/** Utility class for retrieving the version of the application. */
public class Version implements CommandLine.IVersionProvider {

    /**
     * Get the version of the application.
     *
     * @return The version of the application.
     */
    public static String get() {
        String version = Version.class.getPackage().getImplementationVersion();
        return version != null ? version : "0.0.0";
    }

    @Override
    public String[] getVersion() throws Exception {
        return new String[] {Version.get()};
    }
}
