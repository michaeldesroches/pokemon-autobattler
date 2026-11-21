#!/usr/bin/env bash
# Compile le jeu en un jar exécutable (Java 8+ en cible, compatible CheerpJ).
# Usage : ./build.sh        puis  java -jar autobattler.jar
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build
mkdir -p build/classes
javac --release 8 -Xlint:-options -encoding UTF-8 -d build/classes src/autobattler/*.java
printf 'Main-Class: autobattler.Main\n' > build/MANIFEST.MF
jar cfm autobattler.jar build/MANIFEST.MF -C build/classes .
echo "OK : autobattler.jar"
