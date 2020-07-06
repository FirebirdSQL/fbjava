#!/bin/sh
set -e

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

if [ "$OS" = "Windows_NT" ]; then
	THIS_DIR=`echo "$THIS_DIR" | tr \\\\ /`
fi

BASE_DIR=$THIS_DIR

cd $BASE_DIR

./run-maven.sh --projects fbjava javadoc:javadoc

rm -rf apidocs
cp -rf build/java/fbjava/site/apidocs $BASE_DIR
