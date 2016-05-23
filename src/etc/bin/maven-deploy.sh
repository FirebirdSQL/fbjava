#!/bin/sh

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

if [ "$OS" = "Windows_NT" ]; then
	THIS_DIR=`echo "$THIS_DIR" | tr \\\\ /`
fi

BASE_DIR=$THIS_DIR/../../..

cd $BASE_DIR/src
mvn clean package deploy -DaltDeploymentRepository=id::default::$1
