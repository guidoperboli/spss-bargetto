@echo off
rem Convenient execution script for CPSSP Java Solver (Windows)

setlocal
set DIR=%~dp0
set JAR=%DIR%target\cpssp-solver-1.0.0.jar

set CP=%JAR%
if defined GUROBI_HOME (
    if exist "%GUROBI_HOME%\lib\gurobi.jar" (
        set CP=%JAR%;%GUROBI_HOME%\lib\gurobi.jar
    )
)

java -cp "%CP%" cpssp.Main %*
endlocal
