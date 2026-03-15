@echo off
:: =============================================================================
:: Fararoni Suite v1.0.0 - Lanzador Windows (doble-clic)
::
:: Este archivo .bat se ejecuta al hacer doble-clic en Explorer.
:: Eleva a Administrador automaticamente si es posible.
:: =============================================================================

:: Detectar directorio del .bat
cd /d "%~dp0"

:: Buscar installer.ps1 en el directorio actual o en "Fararoni Suite"
if exist ".\installer.ps1" (
    set "SUITE_DIR=%~dp0"
) else if exist ".\Fararoni Suite\installer.ps1" (
    set "SUITE_DIR=%~dp0Fararoni Suite\"
) else (
    echo ERROR: No se encontro installer.ps1
    echo Asegurate de que la carpeta 'Fararoni Suite' este junto a este archivo.
    pause
    exit /b 1
)

:: Solicitar elevacion a Administrador
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Solicitando permisos de Administrador...
    powershell -Command "Start-Process -FilePath 'powershell.exe' -ArgumentList '-ExecutionPolicy Bypass -File \"%SUITE_DIR%installer.ps1\"' -Verb RunAs"
    exit /b
)

:: Ya somos admin, ejecutar directamente
powershell -ExecutionPolicy Bypass -File "%SUITE_DIR%installer.ps1"
