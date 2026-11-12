@echo off
rem Vanilla 1.18.2 noise fill on one E-core (logical processor 20). Arguments: seed side rounds [centerX centerZ]
cd /d %~dp0
start /b /wait /affinity 100000 "" "C:\Program Files\Java\jdk-17\bin\java.exe" @vanilla.args net.minecraft.world.level.levelgen.VanillaFillBench %*
