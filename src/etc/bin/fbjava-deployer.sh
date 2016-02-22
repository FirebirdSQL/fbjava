#!/bin/sh

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

. $THIS_DIR/setenv.sh

$JAVA_HOME/bin/java -cp "$JAR_DIR/*" org.firebirdsql.fbjava.Deployer $@
