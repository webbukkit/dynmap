#!/bin/bash
rm -f *.class *.jar
javac *.java -cp ../hey0/bin/Minecraft_Mod.jar:../minecraft_server.jar && jar cvf map.jar *.class
