@echo off
setlocal

set THIS_DIR=%~dp0

call "%THIS_DIR%\setenv.bat"

set PATH=%FB_DIR%\bin;%PATH%

"%JAVA_HOME%\bin\java" -cp "%JAR_DIR%\*" ^
	"-Djava.library.path=%NATIVE_DIR%" ^
	org.firebirdsql.fbjava.Deployer %*
