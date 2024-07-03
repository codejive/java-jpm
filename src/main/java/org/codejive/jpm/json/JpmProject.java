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

public class JpmProject {
    public Map<String, String> dependencies;
    public Map<String, String> commands;

    public static JpmProject read() throws IOException {
        Path prjJson = Path.of("jpm.json");
        JpmProject prj;
        if (Files.isRegularFile(prjJson)) {
            try (Reader in = Files.newBufferedReader(prjJson)) {
                Gson parser = new GsonBuilder().create();
                prj = parser.fromJson(in, JpmProject.class);
            }
        } else {
            prj = new JpmProject();
        }
        if (prj.dependencies == null) {
            prj.dependencies = new TreeMap<>();
        } else {
            prj.dependencies = new TreeMap<>(prj.dependencies);
        }
        if (prj.commands == null) {
            prj.commands = new TreeMap<>();
        } else {
            prj.commands = new TreeMap<>(prj.commands);
        }
        return prj;
    }

    public static void write(JpmProject prj) throws IOException {
        Path prjJson = Path.of("jpm.json");
        try (Writer out = Files.newBufferedWriter(prjJson)) {
            Gson parser = new GsonBuilder().setPrettyPrinting().create();
            parser.toJson(prj, out);
        }
    }
}
