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
echo --- Тест 1: Тестовый локальный репозиторий (Пакет A) ---
java -cp src DependencyVisualizer --pkg A --repo tests\test_repo.txt --out graph.svg --test

echo.
echo --- Тест 2: Реальный репозиторий Cargo (Пакет serde) ---
java -cp src DependencyVisualizer --pkg serde --repo https://crates.io/api/v1/crates --out graph.svg

pause