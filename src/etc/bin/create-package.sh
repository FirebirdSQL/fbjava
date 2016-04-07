#!/bin/sh

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

BASE_DIR=$THIS_DIR/../../..
TARGET_DIR=`readlink -f $1`
CONFIG=release

cd $BASE_DIR
make TARGET=$CONFIG

cd $BASE_DIR/src/fbjava
mvn package dependency:copy-dependencies

mkdir -p \
	$TARGET_DIR/bin \
	$TARGET_DIR/conf \
	$TARGET_DIR/jar \
	$TARGET_DIR/lib \
	$TARGET_DIR/scripts

cp $BASE_DIR/src/etc/bin/setenv.sh $TARGET_DIR/bin
cp $BASE_DIR/src/etc/bin/fbjava-deployer.sh $TARGET_DIR/bin
cp $BASE_DIR/src/fbjava/target/*.jar $TARGET_DIR/jar
cp $BASE_DIR/src/fbjava/target/dependency/*.jar $TARGET_DIR/jar
cp $BASE_DIR/output/$CONFIG/lib/libfbjava.so $TARGET_DIR/lib
cp $BASE_DIR/src/fbjava/src/main/resources/org/firebirdsql/fbjava/*.sql $TARGET_DIR/scripts
cp $BASE_DIR/src/etc/scripts/*.sql $TARGET_DIR/scripts

if [ -f $TARGET_DIR/conf/java-security.fdb ]; then
	rm $TARGET_DIR/conf/java-security.fdb
fi

echo "create database '$TARGET_DIR/conf/java-security.fdb' default character set utf8;" | isql -q
isql $TARGET_DIR/conf/java-security.fdb -q -i $TARGET_DIR/scripts/java-security.sql
