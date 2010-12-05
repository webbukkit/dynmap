@ECHO OFF

CD E:\Users\Fescen9\workspace\DynamicMap\branches\fescen9
del *.class
del ..\..\..\map.jar
javac *.java -cp ..\..\..\Minecraft_Mod.jar;..\..\..\minecraft_server.jar
jar cvf ..\..\..\map.jar *.class

PAUSE