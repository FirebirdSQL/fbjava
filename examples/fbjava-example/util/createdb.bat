@echo off
setlocal

set FB_BIN=%FIREBIRD%
set FBJAVA_BIN=%FBJAVA_ROOT%\bin

set THIS_DIR=%~dp0

pushd %THIS_DIR%..
set EXAMPLES_ROOT=%cd%
popd

REM Configure some permissions necessary for our test.

%FB_BIN%\isql -q %FBJAVA_ROOT%\conf\java-security.fdb -i util\security.sql
echo execute procedure setup_fbjava_example('%EXAMPLES_ROOT%', '\'); | %FB_BIN%\isql -q %FBJAVA_ROOT%\conf\java-security.fdb
echo drop procedure setup_fbjava_example; | %FB_BIN%\isql -q %FBJAVA_ROOT%\conf\java-security.fdb

REM Create an empty database.
echo create database '%EXAMPLES_ROOT%\db.fdb' default character set utf8; | %FB_BIN%\isql -q

REM Install the Java plugin.
call %FBJAVA_BIN%\fbjava-deployer.bat --database embedded:%EXAMPLES_ROOT%\db.fdb ^
  --install-plugin

REM Store our JAR dependencies on the database.
for %%i in (%EXAMPLES_ROOT%\target\dependency\*.jar) do (
  call %FBJAVA_BIN%\fbjava-deployer.bat --database embedded:%EXAMPLES_ROOT%\db.fdb ^
    --install-jar %%i %%~xni
)

REM Store our JAR on the database.
call %FBJAVA_BIN%\fbjava-deployer.bat --database embedded:%EXAMPLES_ROOT%\db.fdb ^
  --install-jar %EXAMPLES_ROOT%\target\fbjava-example-1.0.0-alpha-1.jar fbjava-example-1.0.0-alpha-1.jar

REM Create the metadata.
%FB_BIN%\isql -q %EXAMPLES_ROOT%\db.fdb -i %THIS_DIR%\database.sql
%FB_BIN%\isql -q %EXAMPLES_ROOT%\db.fdb -i %THIS_DIR%\code.sql
