public class DependencyVisualizer {
    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);
            validateConfig(config);
            printConfig(config);
        } catch (IllegalArgumentException e) {
            System.err.println("Ошибка параметров: " + e.getMessage());
            printHelp();
            System.exit(1);
        }
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
            throw new IllegalArgumentException("Отсутствует значение для параметра " + arg);
        }
        return assignConfigValue(arg, args[index + 1], config, index);
    }
    private static int assignConfigValue(String key, String value, Config config, int index) {
        if ("--pkg".equals(key)) {
            config.packageName = value;
        } else if ("--repo".equals(key)) {
            config.repoPath = value;
        } else if ("--out".equals(key)) {
            config.outputFileName = value;
        } else {
            throw new IllegalArgumentException("Неизвестный параметр: " + key);
        }
        return index + 1;
    }
    private static void validateConfig(Config config) {
        if (config.packageName == null || config.packageName.isEmpty()) {
            throw new IllegalArgumentException("Не задано имя пакета (--pkg)");
        }
        if (config.repoPath == null || config.repoPath.isEmpty()) {
            throw new IllegalArgumentException("Не задан путь к репозиторию (--repo)");
        }
        if (config.outputFileName == null || config.outputFileName.isEmpty()) {
            throw new IllegalArgumentException("Не задано имя выходного файла (--out)");
        }
    }
    private static void printConfig(Config config) {
        System.out.println("Конфигурация успешно загружена:");
        System.out.println("Package Name = " + config.packageName);
        System.out.println("Repository = " + config.repoPath);
        System.out.println("Test Mode = " + config.isTestMode);
        System.out.println("Output File = " + config.outputFileName);
    }
    private static void printHelp() {
        System.out.println("Использование: java DependencyVisualizer [опции]");
        System.out.println("Опции:");
        System.out.println("  --pkg <имя>      Имя анализируемого пакета (обязательно)");
        System.out.println("  --repo <путь>    URL репозитория или путь к тестовому файлу (обязательно)");
        System.out.println("  --out <файл>     Имя генерируемого файла изображения (обязательно)");
        System.out.println("  --test           Включить режим работы с тестовым репозиторием");
    }
    private static class Config {
        String packageName;
        String repoPath;
        boolean isTestMode = false;
        String outputFileName;
    }
}