create sequence seq_permission_group;

create table permission_group (
	id bigint not null constraint pergro_pk primary key,
	name varchar(255) not null
);

create table permission (
	permission_group bigint not null constraint per_fk01 references permission_group,
	class_name varchar(255) not null,
	arg1 varchar(255),
	arg2 varchar(255)
);

create table permission_group_grant (
	permission_group bigint not null constraint pergrogra_fk01 references permission_group,
	database_pattern varchar(1024) not null,
	grantee_type varchar(4) not null constraint pergrogra_ck01 check (grantee_type in ('USER', 'ROLE')),
	grantee_pattern varchar(512) not null
);

set term !;

execute block
as
	declare pergro_id type of column permission_group.id;
begin
	-- Common permissions, by default granted to all users.

	insert into permission_group (id, name)
		values (next value for seq_permission_group, 'COMMON')
		returning id into pergro_id;

	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'file.separator', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'java.version', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'java.vendor', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'java.vendor.url', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'line.separator', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'os.*', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'path.separator', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'jna.encoding', 'read');
	insert into permission (permission_group, class_name, arg1, arg2)
		values (:pergro_id, 'java.util.PropertyPermission', 'jna.profiler.prefix', 'read');

	insert into permission_group_grant (permission_group, database_pattern, grantee_type, grantee_pattern)
		values (:pergro_id, '%', 'USER', '%');
end!

set term ;!
