create sequence fb$java$seq!

create exception fb$java$ex ''!

create table fb$java$jar (
	id bigint not null
		constraint fb$java$jar_pk primary key
		using asc index fb$java$jar_id,
	name varchar(64) character set utf8 not null
		constraint fb$java$jar_name_uk unique
		using asc index fb$java$jar_name,
	owner varchar(31) character set utf8 not null
)!

create table fb$java$jar_entry (
	id bigint not null
		constraint fb$java$jar_entry_pk primary key
		using asc index fb$java$jar_entry_id,
	jar bigint not null
		constraint fb$java$jar_entry_jar_fk references fb$java$jar
		using asc index fb$java$jar_entry_jar,
	name varchar(253) character set utf8 not null
		constraint fb$java$jar_entry_name_uk unique
		using asc index fb$java$jar_entry_name,
	content blob not null
)!

create view fb$java$user_jar as
	select id, name
		from fb$java$jar
		where owner = current_user!

create view fb$java$user_jar_entry as
	select je.id, je.jar, je.name, je.content
		from fb$java$jar_entry je
		join fb$java$user_jar uj
			on uj.id = je.jar!

create trigger fb$java$jar_bi
	before insert on fb$java$jar
as
begin
	if (new.owner <> current_user) then
		exception fb$java$ex 'Attempt to store a jar in another user name';
end!

create trigger fb$java$jar_entry_bi
	before insert on fb$java$jar_entry
as
	declare user_jar integer;
begin
	select 1 from fb$java$user_jar where id = new.jar into user_jar;

	if (user_jar is null) then
		exception fb$java$ex 'Attempt to store a jar entry on a jar from another user';
end!

create package sqlj
as
begin
	procedure install_jar (
		url varchar(256) character set utf8 not null,
		name type of column fb$java$jar.name not null
	);

	procedure remove_jar (
		name type of column fb$java$jar.name not null
	);

	procedure replace_jar (
		url varchar(256) character set utf8 not null,
		name type of column fb$java$jar.name not null
	);

	procedure read_jar (
		name type of column fb$java$jar_entry.name not null
	) returns (
		content type of column fb$java$jar_entry.content --not null
	);

	procedure list_dir (
		name type of column fb$java$jar_entry.name not null
	) returns (
		child type of column fb$java$jar_entry.name
	);
end!

create package body sqlj
as
begin
	procedure verbose_install_jar (
		url varchar(256) character set utf8 not null,
		name type of column fb$java$jar.name not null
	) returns (
		class_name type of column fb$java$jar_entry.name not null
	)
	external name 'org.firebirdsql.fbjava.Deployer.verboseInstallJar(String, String, String[])' engine java;

	procedure verbose_remove_jar (
		name type of column fb$java$jar.name not null
	) returns (
		class_name type of column fb$java$jar_entry.name not null
	)
	as
		declare owner type of column fb$java$jar.owner;
	begin
		select owner from fb$java$jar where name = :name into owner;

		if (owner is null) then
			exception fb$java$ex 'Attempt to remove uninstalled JAR';
		else if (owner <> current_user and current_user <> 'SYSDBA') then
			exception fb$java$ex 'Cannot remove a JAR from another user';

		for select name from fb$java$jar_entry
			where jar = (select id from fb$java$jar where name = :name)
			into class_name
			as cursor c do
		begin
			delete from fb$java$jar_entry where current of c;
			suspend;
		end

		delete from fb$java$jar where name = :name;
	end

	procedure install_jar (
		url varchar(256) character set utf8 not null,
		name type of column fb$java$jar.name not null
	)
	as
		declare dummy integer;
	begin
		for select 1 from verbose_install_jar(:url, :name) into dummy do
		begin
		end
	end

	procedure remove_jar (
		name type of column fb$java$jar.name not null
	)
	as
		declare dummy integer;
	begin
		for select 1 from verbose_remove_jar(:name) into dummy do
		begin
		end
	end

	procedure replace_jar (
		url varchar(256) character set utf8 not null,
		name type of column fb$java$jar.name not null
	)
	as
	begin
		execute procedure remove_jar(name);
		execute procedure install_jar(url, name);
	end

	procedure read_jar (
		name type of column fb$java$jar_entry.name not null
	) returns (
		content type of column fb$java$jar_entry.content --not null
	)
	as
		declare owner type of column fb$java$jar.owner;
	begin
		select je.content, j.owner
			from fb$java$jar_entry je
			join fb$java$jar j
				on j.id = je.jar
			where je.name = :name
			into content, owner;

		if (owner <> current_user and current_user <> 'SYSDBA') then
			exception fb$java$ex 'Cannot read a JAR from another user';

		if (content is not null) then
			suspend;
	end

	procedure list_dir (
		name type of column fb$java$jar_entry.name not null
	) returns (
		child type of column fb$java$jar_entry.name
	)
	as
		declare len integer;
	begin
		len = char_length(name);
		if (not len = 0) then
		begin
			if (substring(name from len for 1) <> '/') then
			begin
				name = name || '/';
				len = len + 1;
			end
		end

		for select distinct substring(je.name from :len + 1 for
				position('/', je.name || '/', :len + 1) - (:len + 1))
			from fb$java$jar_entry je
			join fb$java$jar j
				on j.id = je.jar
			where je.name starting with :name and
				  not (owner <> current_user and current_user <> 'SYSDBA')
			into child
		do
		begin
			suspend;
		end
	end
end!

grant all on table fb$java$jar to package sqlj!
grant all on table fb$java$jar_entry to package sqlj!

grant insert on table fb$java$jar to public!
grant insert on table fb$java$jar_entry to public!

grant select on fb$java$user_jar to public!
grant select on fb$java$user_jar_entry to public!

grant execute on package sqlj to public!
