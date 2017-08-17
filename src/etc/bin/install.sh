#!/bin/sh

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

if [ "`whoami`" != "root" ]; then
	echo "This script must be run as root" 1>&2
	exit 1
fi

if [ "$#" != 1 ]; then
	echo "Syntax: $0 <install-dir>"
	exit 1
fi

TARGET_DIR=$1

if [ -e $TARGET_DIR ]; then
	echo "Install directory should not exist" 1>&2
	exit 1
fi

mkdir $TARGET_DIR
tar xzvf $THIS_DIR/buildroot.tar.gz --directory $TARGET_DIR > /dev/null

# Set file owners
chown -R root:root $TARGET_DIR
chown firebird:firebird $TARGET_DIR/conf/java-security.fdb

echo "You must manually add the following line to <firebird-root>/plugins.conf:"
echo "include $TARGET_DIR/conf/fbjava.conf"
echo
echo "And set the parameter JavaHome in $TARGET_DIR/conf/fbjava.conf to a JRE/JDK install path."
