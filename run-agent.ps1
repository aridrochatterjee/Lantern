$ErrorActionPreference = 'Stop'
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory out | Out-Null
$files = Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object FullName
javac -d out $files
java -cp out com.lantern.agent.server.LanternAgent @args
