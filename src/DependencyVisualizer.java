import java.io.BufferedReader;
import java.io.IOException;
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
            
            System.out.println("\nРебра графа зависимостей:");
            if (edges.isEmpty()) {
                System.out.println("  (зависимостей нет)");
            } else {
                for (String edge : edges) {
                    System.out.println("  " + edge);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }
    private static List<String> buildGraphBFS(String rootPackage, Config config) {
        List<String> edges = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(rootPackage);
        visited.add(rootPackage);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> deps = fetchDependencies(current, config);

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
        String arg = args[index];
        if ("--test".equals(arg)) {
            config.isTestMode = true;
            return index;
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("Нет значения для параметра " + arg);
        }
        return assignConfigValue(arg, args[index + 1], config, index);
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
    private static List<String> fetchDependencies(String packageName, Config config) {
        if (config.isTestMode) {
            return fetchFromTestFile(packageName, config.repoPath);
        } else {
            return fetchFromRegistry(packageName, config.repoPath);
        }
    }
    private static List<String> fetchFromTestFile(String packageName, String filePath) {
        List<String> deps = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            for (String line : lines) {
                if (line.startsWith(packageName + ":")) {
                    String[] parts = line.substring(packageName.length() + 1).split(",");
                    for (String part : parts) {
                        String dep = part.trim();
                        if (!dep.isEmpty()) deps.add(dep);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения: " + e.getMessage());
        }
        return deps;
    }
    private static List<String> fetchFromRegistry(String packageName, String repoUrl) {
        try {
            String version = getLatestVersion(packageName, repoUrl);
            String depsUrl = repoUrl + "/" + packageName + "/" + version + "/dependencies";
            String json = sendHttpGet(depsUrl);
            return parseDependenciesFromJson(json);
        } catch (Exception e) {
            // Если пакет не найден или произошла ошибка сети, возвращаем пустой список
            return new ArrayList<>();
        }
    }
    private static String getLatestVersion(String packageName, String repoUrl) {
        String url = repoUrl + "/" + packageName;
        String json = sendHttpGet(url);
        Matcher matcher = Pattern.compile("\"max_version\":\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new RuntimeException("Не удалось определить версию");
    }
    private static List<String> parseDependenciesFromJson(String json) {
        Set<String> deps = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\"crate_id\":\"([^\"]+)\"").matcher(json);
        while (matcher.find()) {
            deps.add(matcher.group(1));
        }
        return new ArrayList<>(deps);
    }
    private static String sendHttpGet(String urlString) {
        try {
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
        } catch (IOException e) {
            throw new RuntimeException("Ошибка HTTP: " + e.getMessage());
        }
    }
    private static class Config {
        String packageName;
        String repoPath;
        boolean isTestMode = false;
        String outputFileName;
    }
}