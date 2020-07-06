@echo off
setlocal

set FB_BIN=%FIREBIRD%
set FBJAVA_BIN=%FBJAVA_ROOT%\bin

set THIS_DIR=%~dp0

set EXAMPLES_ROOT=%THIS_DIR%..

REM Replace our JAR.
call %FBJAVA_BIN%\fbjava-deployer.bat --database embedded:%EXAMPLES_ROOT%\db.fdb ^
  --replace-jar %EXAMPLES_ROOT%\target\fbjava-example-1.0.0-beta-1.jar fbjava-example-1.0.0-beta-1.jar
