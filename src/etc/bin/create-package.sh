#!/bin/sh

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

if [ "$OS" = "Windows_NT" ]; then
	THIS_DIR=`echo "$THIS_DIR" | tr \\\\ /`
fi

BASE_DIR=$THIS_DIR/../../..
TARGET_DIR=`readlink -f $1`
CONFIG=release

if [ "$OS" = "Windows_NT" ]; then
	SHRLIB_EXT=dll
	SHELL_EXT=bat
else
	SHRLIB_EXT=so
	SHELL_EXT=sh
fi

cd $BASE_DIR
make TARGET=$CONFIG clean all

cd $BASE_DIR/src
mvn clean package dependency:copy-dependencies

cd fbjava
mvn javadoc:javadoc

mkdir -p \
	$TARGET_DIR/bin \
	$TARGET_DIR/conf \
	$TARGET_DIR/jar \
	$TARGET_DIR/docs \
	$TARGET_DIR/lib \
	$TARGET_DIR/scripts

cp $BASE_DIR/src/etc/bin/setenv.$SHELL_EXT $TARGET_DIR/bin
cp $BASE_DIR/src/etc/bin/fbjava-deployer.$SHELL_EXT $TARGET_DIR/bin
cp $BASE_DIR/src/fbjava/target/*.jar $TARGET_DIR/jar
cp $BASE_DIR/src/fbjava-impl/target/*.jar $TARGET_DIR/jar
cp $BASE_DIR/src/fbjava-impl/target/dependency/*.jar $TARGET_DIR/jar
cp -r $BASE_DIR/src/fbjava/target/site/apidocs $TARGET_DIR/docs
cp $BASE_DIR/src/etc/doc/fbjava.pdf $TARGET_DIR/docs
cp $BASE_DIR/output/$CONFIG/lib/libfbjava.$SHRLIB_EXT $TARGET_DIR/lib
cp $BASE_DIR/src/fbjava-impl/src/main/resources/org/firebirdsql/fbjava/*.sql $TARGET_DIR/scripts
cp $BASE_DIR/src/etc/conf/fbjava.conf $TARGET_DIR/conf
cp $BASE_DIR/src/etc/scripts/java-security.sql $TARGET_DIR/scripts

cd $TARGET_DIR/conf/

if [ -f java-security.fdb ]; then
	rm java-security.fdb
fi

echo "create database 'java-security.fdb' default character set utf8;" | isql -q
isql $TARGET_DIR/conf/java-security.fdb -q -i $TARGET_DIR/scripts/java-security.sql

# Transform file name to lower case in Windows
mv java-security.fdb java-security.tmp
mv java-security.tmp java-security.fdb
