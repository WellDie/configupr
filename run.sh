#!/bin/bash

echo "--- Компиляция ---"
javac src/DependencyVisualizer.java
if [ $? -ne 0 ]; then
    echo "Ошибка компиляции"
    exit 1
fi

echo -e "\n--- Тест 1: Успешный парсинг аргументов ---"
java -cp src DependencyVisualizer --pkg reqwest --repo tests/test_repo.txt --out graph.svg --test

echo -e "\n--- Тест 2: Ошибка (отсутствует аргумент) ---"
java -cp src DependencyVisualizer --pkg reqwest --out graph.svg

echo -e "\n--- Тест 3: Ошибка (неизвестный параметр) ---"
java -cp src DependencyVisualizer --pkg reqwest --repo url --out graph.svg --unknown param