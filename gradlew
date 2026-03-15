#!/bin/sh
#
# Gradle wrapper script for BSR Pro Android
#

APP_HOME=`pwd -P`
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

exec "$JAVACMD" "$@" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
