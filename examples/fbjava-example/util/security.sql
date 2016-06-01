set term !;

create or alter procedure setup_fbjava_example (
  root varchar(512),
  separator char(1)
)
as
  declare function esc(i varchar(512)) returns varchar(512)
  as
  begin
    return replace(i, '-', '&-');
  end

  declare pergro_id type of column permission_group.id;
begin
  merge into permission_group
    using rdb$database
    on name = 'fbjava-example'
    when matched then
      update set name = name
    when not matched then
      insert (id, name) values (next value for seq_permission_group, 'fbjava-example')
    returning new.id into pergro_id;

  -- Read permission in the directory.
  update or insert into permission (permission_group, class_name, arg1, arg2)
    values (:pergro_id, 'java.io.FilePermission', :root, 'read')
    matching (permission_group, class_name, arg1, arg2);

  -- Write permission in the log file.
  update or insert into permission (permission_group, class_name, arg1, arg2)
    values (:pergro_id, 'java.io.FilePermission', :root || :separator || 'db.log', 'write')
    matching (permission_group, class_name, arg1, arg2);

  -- Allow connections to localhost, to interact with the postgresql server.
  update or insert into permission (permission_group, class_name, arg1, arg2)
    values (:pergro_id, 'java.net.SocketPermission', 'localhost', 'connect')
    matching (permission_group, class_name, arg1, arg2);

  update or insert into permission_group_grant
    (permission_group, database_pattern, grantee_type, grantee_pattern)
    values (:pergro_id, esc(:root || :separator || 'db.fdb'), 'USER', '%')
    matching (permission_group);
end!

set term ;!
