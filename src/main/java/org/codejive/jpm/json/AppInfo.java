package org.codejive.jpm.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class AppInfo {
    public Map<String, String> dependencies;

    public static final String APP_INFO_FILE = "app.json";

    public static AppInfo read() throws IOException {
        Path prjJson = Path.of(APP_INFO_FILE);
        AppInfo prj;
        if (Files.isRegularFile(prjJson)) {
            try (Reader in = Files.newBufferedReader(prjJson)) {
                Gson parser = new GsonBuilder().create();
                prj = parser.fromJson(in, AppInfo.class);
            }
        } else {
            prj = new AppInfo();
        }
        if (prj.dependencies == null) {
            prj.dependencies = new TreeMap<>();
        } else {
            prj.dependencies = new TreeMap<>(prj.dependencies);
        }
        return prj;
    }

    public static void write(AppInfo prj) throws IOException {
        Path prjJson = Path.of(APP_INFO_FILE);
        try (Writer out = Files.newBufferedWriter(prjJson)) {
            Gson parser = new GsonBuilder().setPrettyPrinting().create();
            parser.toJson(prj, out);
        }
    }
}
