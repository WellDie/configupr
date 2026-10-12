@echo off
chcp 65001>nul

echo --- Компиляция ---
javac src\DependencyVisualizer.java
if errorlevel 1 (
    echo Ошибка компиляции
    pause
    exit /b
)

echo.
echo --- Тест 1: Успешный парсинг аргументов ---
java -cp src DependencyVisualizer --pkg reqwest --repo tests\test_repo.txt --out graph.svg --test

echo.
echo --- Тест 2: Ошибка (отсутствует аргумент) ---
java -cp src DependencyVisualizer --pkg reqwest --out graph.svg

echo.
echo --- Тест 3: Ошибка (неизвестный параметр) ---
java -cp src DependencyVisualizer --pkg reqwest --repo url --out graph.svg --unknown param

pause