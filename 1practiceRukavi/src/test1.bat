@echo off
echo Запуск с минимальной VFS и без скрипта
java ShellEmulator minimal_vfs_folder
pause

echo Запуск с большой VFS и стартовым скриптом
java ShellEmulator complex_vfs_folder startup.txt
pause