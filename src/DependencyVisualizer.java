import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyVisualizer {

    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);
            validateConfig(config);
            
            System.out.println("Построение графа (BFS) для: " + config.packageName);
            List<String> edges = buildGraphBFS(config.packageName, config);
            
            System.out.println("\nРебра графа:");
            edges.forEach(e -> System.out.println("  " + e));
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<String> buildGraphBFS(String rootPackage, Config config) {
        List<String> edges = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Map<String, List<String>> cache = new HashMap<>();

        queue.add(rootPackage);
        visited.add(rootPackage);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> deps = cache.computeIfAbsent(current, k -> fetchDependencies(k, config));

            for (String dep : deps) {
                edges.add(current + " -> " + dep);
                if (!visited.contains(dep)) {
                    visited.add(dep);
                    queue.add(dep);
                }
            }
        }
        return edges;
    }

    private static Config parseArgs(String[] args) {
        Config config = new Config();
        for (int i = 0; i < args.length; i++) {
            i = processSingleArg(args, i, config);
        }
        return config;
    }

    private static int processSingleArg(String[] args, int index, Config config) {
        if ("--test".equals(args[index])) {
            config.isTestMode = true;
            return index;
        }
        if (index + 1 >= args.length) throw new IllegalArgumentException("Нет значения: " + args[index]);
        return assignConfigValue(args[index], args[index + 1], config, index);
    }

    private static int assignConfigValue(String key, String value, Config config, int index) {
        switch (key) {
            case "--pkg": config.packageName = value; break;
            case "--repo": config.repoPath = value; break;
            case "--out": config.outputFileName = value; break;
            default: throw new IllegalArgumentException("Неизвестный параметр: " + key);
        }
        return index + 1;
    }

    private static void validateConfig(Config config) {
        if (config.packageName == null) throw new IllegalArgumentException("Не задан --pkg");
        if (config.repoPath == null) throw new IllegalArgumentException("Не задан --repo");
        if (config.outputFileName == null) throw new IllegalArgumentException("Не задан --out");
    }

    private static List<String> fetchDependencies(String pkg, Config config) {
        return config.isTestMode ? fetchFromTestFile(pkg, config.repoPath) : fetchFromRegistry(pkg, config.repoPath);
    }

    private static List<String> fetchFromTestFile(String pkg, String filePath) {
        try {
            for (String line : Files.readAllLines(Paths.get(filePath))) {
                if (line.startsWith(pkg + ":")) {
                    List<String> deps = new ArrayList<>();
                    for (String p : line.substring(pkg.length() + 1).split(",")) {
                        if (!p.trim().isEmpty()) deps.add(p.trim());
                    }
                    return deps;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private static List<String> fetchFromRegistry(String pkg, String repoUrl) {
        try {
            String versionJson = sendHttpGet(repoUrl + "/" + pkg);
            Matcher vMatcher = Pattern.compile("\"max_version\":\"([^\"]+)\"").matcher(versionJson);
            if (!vMatcher.find()) return new ArrayList<>();
            
            String depsJson = sendHttpGet(repoUrl + "/" + pkg + "/" + vMatcher.group(1) + "/dependencies");
            Set<String> deps = new LinkedHashSet<>();
            Matcher dMatcher = Pattern.compile("\"crate_id\":\"([^\"]+)\"").matcher(depsJson);
            while (dMatcher.find()) deps.add(dMatcher.group(1));
            return new ArrayList<>(deps);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String sendHttpGet(String urlString) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "ConfigPractice22/1.0");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    private static class Config {
        String packageName, repoPath, outputFileName;
        boolean isTestMode = false;
    }
}