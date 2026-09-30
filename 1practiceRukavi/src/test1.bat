@echo off
chcp 65001>nul

echo Compilation...
javac ShellEmulator.java

if errorlevel 1(
echo error compilation
pause
exit /b
)

echo minimal
java ShellEmulator minimal_vfs_folder
pause

echo complex
java ShellEmulator complex_vfs_folder startup.txt
pause