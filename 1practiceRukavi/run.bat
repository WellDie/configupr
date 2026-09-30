@echo off
chcp 65001>nul

echo Compilation...
javac src\ShellEmulator.java
if errorlevel 1 (
    echo error compilation
    pause
    exit /b
)

echo Running minimal test...
java -cp src ShellEmulator tests\minimal_vfs_folder
pause

echo Running complex test with script...
java -cp src ShellEmulator tests\complex_vfs_folder tests\startup.txt
pause