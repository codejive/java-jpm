package org.codejive.jpm.util;

import picocli.CommandLine;

public class Version implements CommandLine.IVersionProvider {
    public static String get() {
        String version = Version.class.getPackage().getImplementationVersion();
        return version != null ? version : "0.0.0";
    }

    @Override
    public String[] getVersion() throws Exception {
        return new String[] {Version.get()};
    }
}
