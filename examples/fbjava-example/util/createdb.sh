#!/bin/sh

FB_BIN=$FIREBIRD/bin
FBJAVA_BIN=$FBJAVA_ROOT/bin

THIS_DIR=`readlink -f $0`
THIS_DIR=`dirname $THIS_DIR`

EXAMPLES_ROOT=$THIS_DIR/..
EXAMPLES_ROOT=`readlink -f $EXAMPLES_ROOT`

# Configure some permissions necessary for our test.

$FB_BIN/isql -q $FBJAVA_ROOT/conf/java-security.fdb <<EOF
set term !;

execute block
as
  declare function esc(i varchar(512)) returns varchar(512)
  as
  begin
    return replace(i, '-', '&-');
  end

  declare pergro_id type of column permission_group.id;
begin
  update or insert into permission_group (id, name)
    values (next value for seq_permission_group, 'fbjava-example')
    matching (id)
    returning id into pergro_id;

  -- Read permission in the directory.
  update or insert into permission (permission_group, class_name, arg1, arg2)
    values (:pergro_id, 'java.io.FilePermission', '$EXAMPLES_ROOT', 'read')
    matching (permission_group, class_name, arg1, arg2);

  -- Write permission in the log file.
  update or insert into permission (permission_group, class_name, arg1, arg2)
    values (:pergro_id, 'java.io.FilePermission', '$EXAMPLES_ROOT/db.log', 'write')
    matching (permission_group, class_name, arg1, arg2);

  -- Allow connections to localhost, to interact with the postgresql server.
  update or insert into permission (permission_group, class_name, arg1, arg2)
    values (:pergro_id, 'java.net.SocketPermission', 'localhost', 'connect')
    matching (permission_group, class_name, arg1, arg2);

  update or insert into permission_group_grant
    (permission_group, database_pattern, grantee_type, grantee_pattern)
    values (:pergro_id, esc('$EXAMPLES_ROOT/db.fdb'), 'USER', '$ISC_USER')
    matching (permission_group);
end!

commit!
EOF

# Create an empty database.
echo "create database '$EXAMPLES_ROOT/db.fdb' default character set utf8;" | $FB_BIN/isql -q

# Install the Java plugin.
$FBJAVA_BIN/fbjava-deployer.sh --database embedded:$EXAMPLES_ROOT/db.fdb \
  --user $ISC_USER --password $ISC_PASSWORD \
  --install-plugin

# Store our JAR dependencies on the database.
for line in `find $EXAMPLES_ROOT/target/dependency -name '*.jar' -print |xargs -0 echo`
do
  $FBJAVA_BIN/fbjava-deployer.sh --database embedded:$EXAMPLES_ROOT/db.fdb \
    --user $ISC_USER --password $ISC_PASSWORD \
    --install-jar $line `basename $line`
done

# Store our JAR on the database.
$FBJAVA_BIN/fbjava-deployer.sh --database embedded:$EXAMPLES_ROOT/db.fdb \
  --user $ISC_USER --password $ISC_PASSWORD \
  --install-jar $EXAMPLES_ROOT/target/fbjava-example-1.0.0-alpha-1.jar fbjava-example-1.0.0-alpha-1.jar

# Create the metadata.
$FB_BIN/isql -q $EXAMPLES_ROOT/db.fdb -i $THIS_DIR/database.sql
$FB_BIN/isql -q $EXAMPLES_ROOT/db.fdb -i $THIS_DIR/code.sql
