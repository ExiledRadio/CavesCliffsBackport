@echo off
rem Runs a compiled tool class (build\terrain-tools, see run.sh) on one E-core (logical processor 20).
rem Usage: bench.cmd <8|17|21|25> <MainClass> [args...]
cd /d %~dp0..\..
set JAVA=C:\Program Files\Java\jdk-1.8\bin\java.exe
if "%1"=="17" set JAVA=C:\Program Files\Java\jdk-17\bin\java.exe
if "%1"=="21" set JAVA=C:\Program Files\Java\jdk-21.0.11\bin\java.exe
if "%1"=="25" set JAVA=C:\Program Files\Java\jdk-25.0.3\bin\java.exe
start /b /wait /affinity 100000 "" "%JAVA%" -Xmx1G -cp build\terrain-tools %2 %3 %4 %5 %6 %7 %8 %9
