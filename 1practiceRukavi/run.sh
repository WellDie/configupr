#!/bin/bash
echo "Compilation..."
javac src/ShellEmulator.java
if [ $? -ne 0 ]; then
    echo "error compilation"
    exit 1
fi

echo "Running minimal test..."
java -cp src ShellEmulator tests/minimal_vfs_folder
read -p "Press Enter to continue..."

echo "Running complex test with script..."
java -cp src ShellEmulator tests/complex_vfs_folder tests/startup.txt