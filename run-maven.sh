#!/bin/sh
mvn -f src -Dbuild.dir=`pwd`/build/java $@
