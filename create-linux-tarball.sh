#!/bin/sh
set -e

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

if [ "$#" != 2 ]; then
	echo "Syntax: $0 <install-dir> <target-tar-gz-file>"
	exit 1
fi

INSTALL_DIR=`readlink -f $1`
TARGET_FILE=`readlink -f $2`

BASE_NAME=`basename $TARGET_FILE`
BASE_NAME=${BASE_NAME%.tar.gz}

if [ ! -f $INSTALL_DIR/conf/java-security.fdb ]; then
	echo "Invalid installation directory" 1>&2
	exit 1
fi

TEMP_DIR=`mktemp -d`
TARGET_DIR=$TEMP_DIR/$BASE_NAME/temp

mkdir -p $TARGET_DIR
cp -rf $INSTALL_DIR/* $TARGET_DIR

# Set file permissions
find $TARGET_DIR -type d -exec chmod 755 {} \;
find $TARGET_DIR -type f -exec chmod 444 {} \;
chmod +x $TARGET_DIR/bin/fbjava-deployer.sh $TARGET_DIR/examples/fbjava-example/util/*.sh
chmod 660 $TARGET_DIR/conf/java-security.fdb
find $TARGET_DIR/examples -type f -exec chmod +w {} \;
find $TARGET_DIR/lib -type f -exec chmod 555 {} \;

cd $TARGET_DIR

tar czvf ../buildroot.tar.gz --owner=root --group=root .

cd ..
rm -rf temp

cp $THIS_DIR/src/etc/bin/install.sh .

cd ..
tar czvf $TARGET_FILE --owner=root --group=root $BASE_NAME/*

rm -rf $TEMP_DIR
