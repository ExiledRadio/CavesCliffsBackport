@echo off
rem Runs a newer vanilla oracle class on the E-cores (JDK 17, Mojang-named client jar and its libraries).
rem Usage: run.cmd <119|120> <class> [args...]   (classes compiled into build\oracle<v>, see docs/TERRAIN_CORE.md)
rem JVM options: set ORACLE_OPTS (for example -Dfeatures=... -Dcenters=...).
cd /d %~dp0..\..\..
set V=%1
if "%V%"=="119" set MC=G:\MCDownloads\minecraft-1.19.2
if "%V%"=="120" set MC=G:\MCDownloads\minecraft-1.20.1
start /b /wait /affinity FFFF0000 "" "C:\Program Files\Java\jdk-17\bin\java.exe" -Xmx8G %ORACLE_OPTS% -cp "build\oracle%V%;%MC%\client-named-unsigned.jar;%MC%\libs\*" %2 %3 %4 %5 %6 %7 %8 %9
