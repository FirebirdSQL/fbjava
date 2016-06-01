create or alter trigger employee_log_bdiu before delete or insert or update on employee
  external name 'org.firebirdsql.fbjava.examples.fbjava_example.FbLogger.info()'
  engine java;

create or alter trigger employee_log_adiu after delete or insert or update on employee
  external name 'org.firebirdsql.fbjava.examples.fbjava_example.FbLogger.info()'
  engine java;

create or alter function get_system_property (
  name varchar(60)
) returns varchar(60)
  external name 'java.lang.System.getProperty(String)'
  engine java;

create or alter function regex_replace (
  regex varchar(60),
  str varchar(60),
  replacement varchar(60)
) returns varchar(60)
  external name 'org.firebirdsql.fbjava.examples.fbjava_example.FbRegex.replace(String,
      String, String)'
  engine java;

create or alter procedure employee_local (
  dummy integer = 1  -- Firebird 3.0.0 has a bug with external procedures without parameters
) returns (
  id type of column employee.id,
  name type of column employee.name
)
  external name 'org.firebirdsql.fbjava.examples.fbjava_example.FbJdbc.executeQuery()
    !jdbc:default:connection'
  engine java
  as 'select * from employee';

create or alter procedure employee_pgsql (
  dummy integer = 1  -- Firebird 3.0.0 has a bug with external procedures without parameters
) returns (
  id type of column employee.id,
  name type of column employee.name
)
  external name 'org.firebirdsql.fbjava.examples.fbjava_example.FbJdbc.executeQuery()
    !jdbc:postgresql:employee|postgres|postgres'
  engine java
  as 'select * from employee';
