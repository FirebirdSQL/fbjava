#!/bin/sh

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

if [ "$OS" = "Windows_NT" ]; then
	THIS_DIR=`echo "$THIS_DIR" | tr \\\\ /`
fi

BASE_DIR=$THIS_DIR/../../..

rm -rf $BASE_DIR/apidocs
cp -rf $BASE_DIR/src/fbjava/target/site/apidocs $BASE_DIR
