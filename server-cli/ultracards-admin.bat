@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%ultracards-admin.jar"

if not exist "%JAR_PATH%" set "JAR_PATH=%SCRIPT_DIR%target\ultracards-admin.jar"

if not exist "%JAR_PATH%" (
    echo No ultracards-admin.jar found beside this script or in target 1>&2
    exit /b 1
)

java --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" %*
