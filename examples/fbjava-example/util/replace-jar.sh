#!/bin/sh

FB_BIN=$FIREBIRD/bin
FBJAVA_BIN=$FBJAVA_ROOT/bin

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

EXAMPLES_ROOT=$THIS_DIR/..
EXAMPLES_ROOT=`readlink -f $EXAMPLES_ROOT`

# Replace our JAR.
$FBJAVA_BIN/fbjava-deployer.sh --database embedded:$EXAMPLES_ROOT/db.fdb \
  --user $ISC_USER --password $ISC_PASSWORD \
  --replace-jar $EXAMPLES_ROOT/target/fbjava-example-1.0.0-alpha-1.jar fbjava-example-1.0.0-alpha-1.jar
