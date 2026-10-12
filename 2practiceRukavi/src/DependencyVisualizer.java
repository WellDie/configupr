import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Класс для визуализации графа зависимостей (Вариант 22).
 */
public class DependencyVisualizer {

    /**
     * Точка входа в программу.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);
            validateConfig(config);

            System.out.println("Этап 3: Построение графа (BFS) для: " + config.packageName);
            Set<String> edges = buildGraphBFS(config.packageName, config);

            System.out.println("\nЭтап 4: Обратные зависимости пакета '" + config.packageName + "':");
            printReverseDependencies(edges, config.packageName);

            System.out.println("\nЭтап 5: Визуализация PlantUML");
            String puml = generatePlantUml(edges);
            System.out.println("Сгенерированный код:\n" + puml);

            saveSvg(puml, config.outputFileName);
            System.out.println("Успех: Граф сохранен в файл " + config.outputFileName);

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Строит граф зависимостей обходом в ширину (BFS).
     *
     * @param root   корневой пакет
     * @param config конфигурация
     * @return уникальный набор ребер
     */
    private static Set<String> buildGraphBFS(String root, Config config) {
        Set<String> edges = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Map<String, List<String>> cache = new HashMap<>();

        queue.add(root);
        visited.add(root);

        while (!queue.isEmpty()) {
            if (!config.isTestMode && visited.size() > 15) break; // Защита от бесконечных графов в сети
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

    /**
     * Выводит пакеты, которые зависят от целевого пакета.
     *
     * @param edges набор связей
     * @param target целевой пакет
     */
    private static void printReverseDependencies(Set<String> edges, String target) {
        boolean found = false;
        for (String edge : edges) {
            String[] parts = edge.split(" -> ");
            if (parts.length == 2 && parts[1].equals(target)) {
                System.out.println("  - " + parts[0]);
                found = true;
            }
        }
        if (!found) System.out.println("  (обратных зависимостей в графе не найдено)");
    }

    /**
     * Генерирует текст графа в формате PlantUML.
     *
     * @param edges набор ребер
     * @return строка с кодом PlantUML
     */
    private static String generatePlantUml(Set<String> edges) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam componentStyle rectangle\n");
        for (String edge : edges) {
            String[] p = edge.split(" -> ");
            sb.append("[").append(p[0]).append("] --> [").append(p[1]).append("]\n");
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    /**
     * Отправляет PlantUML код на API для рендера и сохраняет в SVG файл.
     *
     * @param puml     текст PlantUML
     * @param filename имя файла для сохранения
     */
    private static void saveSvg(String puml, String filename) throws Exception {
        URL url = URI.create("https://kroki.io/plantuml/svg").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(puml.getBytes("UTF-8"));
        }
        try (InputStream is = conn.getInputStream()) {
            Files.copy(is, Paths.get(filename), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Парсит аргументы CLI.
     *
     * @param args аргументы
     * @return конфигурация
     */
    private static Config parseArgs(String[] args) {
        Config config = new Config();
        for (int i = 0; i < args.length; i++) {
            if ("--test".equals(args[i])) {
                config.isTestMode = true;
            } else if (i + 1 < args.length) {
                assignConfigValue(args[i], args[i + 1], config);
                i++;
            }
        }
        return config;
    }

    /**
     * Назначает параметр в конфигурацию.
     *
     * @param key   ключ
     * @param value значение
     * @param cfg   конфигурация
     */
    private static void assignConfigValue(String key, String value, Config cfg) {
        switch (key) {
            case "--pkg": cfg.packageName = value; break;
            case "--repo": cfg.repoPath = value; break;
            case "--out": cfg.outputFileName = value; break;
        }
    }

    /**
     * Проверяет заполненность конфигурации.
     *
     * @param config конфигурация
     */
    private static void validateConfig(Config config) {
        if (config.packageName == null) throw new IllegalArgumentException("Не задан --pkg");
        if (config.repoPath == null) throw new IllegalArgumentException("Не задан --repo");
        if (config.outputFileName == null) throw new IllegalArgumentException("Не задан --out");
    }

    /**
     * Маршрутизатор получения зависимостей.
     *
     * @param pkg    пакет
     * @param config конфиг
     * @return список зависимостей
     */
    private static List<String> fetchDependencies(String pkg, Config config) {
        return config.isTestMode ? fetchFromTestFile(pkg, config.repoPath) : fetchFromRegistry(pkg, config.repoPath);
    }

    /**
     * Читает из тестового файла.
     *
     * @param pkg      пакет
     * @param filePath путь
     * @return список
     */
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
            System.err.println("Ошибка чтения файла: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Запрашивает через API crates.io.
     *
     * @param pkg     пакет
     * @param repoUrl урл
     * @return список
     */
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

    /**
     * Делает GET запрос.
     *
     * @param urlString урл
     * @return тело ответа
     */
    private static String sendHttpGet(String urlString) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "ConfigPractice22/1.0");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    /**
     * Структура конфига.
     */
    private static class Config {
        String packageName, repoPath, outputFileName;
        boolean isTestMode = false;
    }
}