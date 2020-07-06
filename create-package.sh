#!/bin/sh
set -e

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

if [ "$OS" = "Windows_NT" ]; then
	THIS_DIR=`echo "$THIS_DIR" | tr \\\\ /`
fi

BASE_DIR=$THIS_DIR
TARGET_DIR=`readlink -f "$1"`

if [ "$#" != 1 ]; then
	echo "Syntax: $0 <target-dir>"
	exit 1
fi

# May be set by caller with values "Release" (default) or "Debug".
FBJAVA_CONFIG="${FBJAVA_CONFIG:-Release}"

if [ -d "$TARGET_DIR" ]; then
	echo "Install directory should not exist" 1>&2
	exit 1
fi

if [ "$OS" = "Windows_NT" ]; then
	SHRLIB_EXT=dll
	SHELL_EXT=bat
else
	SHRLIB_EXT=so
	SHELL_EXT=sh
fi

if [ "$OS" != "Windows_NT" ] && [ "$FBJAVA_LINK" = "1" ]; then
	CP="ln -sf"
	CPR="ln -sf"
else
	CP=cp
	CPR="cp -r"
fi

cd $BASE_DIR

if [ -d build ]; then
	rm -r build
fi

./run-maven.sh clean package dependency:copy-dependencies javadoc:javadoc
./gen-cmake.sh $FBJAVA_CONFIG

if [ "$OS" != "Windows_NT" ]; then
	(cd build; make TARGET=$FBJAVA_CONFIG)
else
	(cd build; msbuild.exe fbjava.sln -p:Configuration=$FBJAVA_CONFIG)
fi

mkdir -p \
	"$TARGET_DIR/bin" \
	"$TARGET_DIR/conf" \
	"$TARGET_DIR/jar" \
	"$TARGET_DIR/docs" \
	"$TARGET_DIR/lib" \
	"$TARGET_DIR/scripts" \
	"$TARGET_DIR/examples"

$CP $BASE_DIR/src/etc/bin/setenv.$SHELL_EXT "$TARGET_DIR/bin"
$CP $BASE_DIR/src/etc/bin/fbjava-deployer.$SHELL_EXT "$TARGET_DIR/bin"
$CP $BASE_DIR/build/java/fbjava/*.jar "$TARGET_DIR/jar"
$CP $BASE_DIR/build/java/fbjava-impl/*.jar "$TARGET_DIR/jar"
$CP $BASE_DIR/build/java/fbjava-impl/dependency/*.jar "$TARGET_DIR/jar"
$CPR $BASE_DIR/build/java/fbjava/site/apidocs "$TARGET_DIR/docs"
$CP $BASE_DIR/src/etc/doc/fbjava.pdf "$TARGET_DIR/docs"

if [ "$OS" != "Windows_NT" ]; then
	$CP $BASE_DIR/build/lib/libfbjava.$SHRLIB_EXT "$TARGET_DIR/lib"
else
	$CP $BASE_DIR/build/src/native/fbjava/$FBJAVA_CONFIG/libfbjava.$SHRLIB_EXT "$TARGET_DIR/lib"
fi

$CP $BASE_DIR/src/fbjava-impl/src/main/resources/org/firebirdsql/fbjava/*.sql "$TARGET_DIR/scripts"
cp $BASE_DIR/src/etc/conf/fbjava.conf "$TARGET_DIR/conf"
cp $BASE_DIR/src/etc/conf/jvm.args "$TARGET_DIR/conf"
$CP $BASE_DIR/src/etc/scripts/java-security.sql "$TARGET_DIR/scripts"
$CPR $BASE_DIR/examples/* "$TARGET_DIR/examples"

if [ "$OS" != "Windows_NT" ] && [ "$FBJAVA_LINK" != "1" ]; then
	strip "$TARGET_DIR/lib/"*.$SHRLIB_EXT
fi

if [ -f "$TARGET_DIR/conf/java-security.fdb" ]; then
	rm "$TARGET_DIR/conf/java-security.fdb"
fi

echo "create database '$TARGET_DIR/conf/java-security.fdb' default character set utf8;" | isql -q
isql "$TARGET_DIR/conf/java-security.fdb" -q -i "$TARGET_DIR/scripts/java-security.sql"

if [ "$OS" = "Windows_NT" ]; then
	# Transform file name to lower case in Windows
	mv "$TARGET_DIR/conf/java-security.fdb" "$TARGET_DIR/conf/java-security.tmp"
	mv "$TARGET_DIR/conf/java-security.tmp" "$TARGET_DIR/conf/java-security.fdb"
fi
