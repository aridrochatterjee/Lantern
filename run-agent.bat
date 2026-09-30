@echo off
setlocal
if exist out rmdir /s /q out
mkdir out
for /r src\main\java %%f in (*.java) do echo %%f >> sources.txt
javac -d out @sources.txt
del sources.txt
java -cp out com.lantern.agent.server.LanternAgent %*
endlocal
