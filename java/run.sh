#!/bin/bash
# Convenient execution script for CPSSP Java Solver

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${DIR}/target/cpssp-solver-1.0.0.jar"

# If GUROBI_HOME is defined, append gurobi.jar to classpath
CP="${JAR}"
if [ -n "${GUROBI_HOME}" ] && [ -f "${GUROBI_HOME}/lib/gurobi.jar" ]; then
    CP="${JAR}:${GUROBI_HOME}/lib/gurobi.jar"
fi

java -cp "${CP}" cpssp.Main "$@"
