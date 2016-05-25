# FB/Java plugin for Firebird

JavaDocs: https://rawgit.com/FirebirdSQL/fbjava/master/apidocs/index.html

Use this setting as maven repository:

```
<repository>
	<id>fbjava</id>
	<name>fbjava</name>
	<url>https://raw.githubusercontent.com/asfernandes/fbjava-maven/master/maven2</url>
</repository>
```

And this setting for the artifact dependency:

```
<dependency>
	<groupId>org.firebirdsql.fbjava</groupId>
	<artifactId>fbjava</artifactId>
	<version>1.0.0-alpha-1</version>
	<scope>provided</scope>
</dependency>
```
