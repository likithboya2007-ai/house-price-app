#!/bin/sh
# Requires JDK 11+. Compiles and starts the app on http://localhost:8080
mkdir -p out && javac -d out src/*.java && java -cp out Main "$@"
