# FB/Java plugin for Firebird

Manual: https://rawgit.com/FirebirdSQL/fbjava/1.0.0-alpha-1/src/etc/doc/fbjava.pdf

Java Docs: https://rawgit.com/FirebirdSQL/fbjava/1.0.0-alpha-1/apidocs/index.html

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

## Sponsors

The FB/Java team would like to thank YourKit for providing licenses to their [YourKit Java Profiler](https://www.yourkit.com/java/profiler/). 
![YourKit](https://www.yourkit.com/images/yklogo.png)
