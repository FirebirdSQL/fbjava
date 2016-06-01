create domain d_id as bigint not null;
create domain d_name as varchar(60) character set utf8 not null;

create table employee (
  id d_id,
  name d_name
);
