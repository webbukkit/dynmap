@ECHO OFF

CD E:\Projects\DynamicMap\branches\fescen9

CALL clean.bat

MKDIR plugins
MKDIR plugins\web
MKDIR plugins\web\tiles
MKDIR plugins\web\up

javac *.java -cp ..\..\..\Minecraft_Mod.jar;..\..\..\minecraft_server.jar
jar cvf plugins\map.jar *.class


COPY colors.txt .\plugins
COPY readme.txt .\plugins
COPY .\web\*.* .\plugins\web
COPY .\web\tiles\*.* .\plugins\web\tiles
COPY .\web\up\*.* .\plugins\web\up

CALL "C:\Program Files\WinRAR\Rar.exe" a -m5 -ed -r .\dist\DynamicMap_Fescen9.rar .\plugins\*.*

PAUSE