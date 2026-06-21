@echo off
REM Arcturus Morningstar Extended - Windows start script
REM Requires a Java 25 (JDK/JRE) runtime on PATH.
REM
REM JVM tuning (Java 25):
REM   -XX:+UseZGC                   generational, low-pause GC  -> fewer in-game lag spikes
REM   -XX:+UseCompactObjectHeaders  smaller object headers      -> lower RAM with many furni/items/users
REM
REM Adjust -Xmx to your machine, and fix the path/jar version below.
java -Dfile.encoding=UTF-8 -Xmx4096m -XX:+UseZGC -XX:+UseCompactObjectHeaders -jar PATH_TO_YOUR_EMULATOR\Habbo-4.2.45-jar-with-dependencies.jar
