create sequence seq_permission_group start with 2;

create table permission_group (
	id bigint not null constraint permission_group_pk primary key,
	name varchar(255) not null
);

create table permission (
	permission_group bigint not null constraint permission_pg_fk references permission_group,
	user_name varchar(31) not null,
	class_name varchar(255) not null,
	arg1 varchar(255),
	arg2 varchar(255)
);

create table database_permission_group (
	database_pattern varchar(255) not null,
	permission_group bigint not null constraint database_permission_group_pg_fk references permission_group
);

-- Common permission group
insert into permission_group (id, name)
	values (1, 'COMMON');

-- Public permissions
insert into permission (permission_group, user_name, class_name, arg1, arg2)
	values (1, 'PUBLIC', 'java.util.PropertyPermission', 'file.separator', 'read');
insert into permission (permission_group, user_name, class_name, arg1, arg2)
	values (1, 'PUBLIC', 'java.util.PropertyPermission', 'java.version', 'read');
insert into permission (permission_group, user_name, class_name, arg1, arg2)
	values (1, 'PUBLIC', 'java.util.PropertyPermission', 'java.vendor', 'read');
insert into permission (permission_group, user_name, class_name, arg1, arg2)
	values (1, 'PUBLIC', 'java.util.PropertyPermission', 'java.vendor.url', 'read');
insert into permission (permission_group, user_name, class_name, arg1, arg2)
	values (1, 'PUBLIC', 'java.util.PropertyPermission', 'line.separator', 'read');
insert into permission (permission_group, user_name, class_name, arg1, arg2)
	values (1, 'PUBLIC', 'java.util.PropertyPermission', 'os.*', 'read');
insert into permission (permission_group, user_name, class_name, arg1, arg2)
	values (1, 'PUBLIC', 'java.util.PropertyPermission', 'path.separator', 'read');

-- Common permissions
insert into database_permission_group (database_pattern, permission_group)
	values ('%', 1);
