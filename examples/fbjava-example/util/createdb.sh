#!/bin/sh

FB_BIN=$FIREBIRD/bin
FBJAVA_BIN=$FBJAVA_ROOT/bin

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

EXAMPLES_ROOT=$THIS_DIR/..
EXAMPLES_ROOT=`readlink -f $EXAMPLES_ROOT`

# Configure some permissions necessary for our test.

$FB_BIN/isql -q $FBJAVA_ROOT/conf/java-security.fdb -i util/security.sql
echo "execute procedure setup_fbjava_example('$EXAMPLES_ROOT', '/');" | $FB_BIN/isql -q $FBJAVA_ROOT/conf/java-security.fdb
echo "drop procedure setup_fbjava_example;" | $FB_BIN/isql -q $FBJAVA_ROOT/conf/java-security.fdb

# Create an empty database.
echo "create database '$EXAMPLES_ROOT/db.fdb' default character set utf8;" | $FB_BIN/isql -q

# Install the Java plugin.
$FBJAVA_BIN/fbjava-deployer.sh --database embedded:$EXAMPLES_ROOT/db.fdb \
  --install-plugin

# Store our JAR dependencies on the database.
for line in `find $EXAMPLES_ROOT/target/dependency -name '*.jar' -print |xargs -0 echo`
do
  $FBJAVA_BIN/fbjava-deployer.sh --database embedded:$EXAMPLES_ROOT/db.fdb \
    --install-jar $line `basename $line`
done

# Store our JAR on the database.
$FBJAVA_BIN/fbjava-deployer.sh --database embedded:$EXAMPLES_ROOT/db.fdb \
  --install-jar $EXAMPLES_ROOT/target/fbjava-example-1.0.0-alpha-1.jar fbjava-example-1.0.0-alpha-1.jar

# Create the metadata.
$FB_BIN/isql -q $EXAMPLES_ROOT/db.fdb -i $THIS_DIR/database.sql
$FB_BIN/isql -q $EXAMPLES_ROOT/db.fdb -i $THIS_DIR/code.sql
