#!/bin/bash
#
# start-cmd.sh - starts the application in the FOREGROUND.
#                Console output is visible; press CTRL-C to stop.

export SERVER_HOME=$(cd "$(dirname $(readlink -f "$0"))/..";pwd)
source $SERVER_HOME/bin/config.sh

pid=$(pgrep -f "[j]ava.*$APP" | head -n 1)

if [[ ! -z "$pid" ]]
then
	echo "$APP_NAME is already running on pid $pid."
	exit 1
fi

if [ ! "$APP_USER" == "$(whoami)" ]
then
	echo "$APP_NAME must be run with user '$APP_USER'"
	exit 1
fi

echo
echo "Changing current directory to $SERVER_HOME"
cd "$SERVER_HOME"

if [ -z "$JAVA_HOME" ]; then
	JAVA_CMD="java"
	javaPath=$(readlink -nf $(which java) | xargs dirname | xargs dirname | xargs dirname)
	echo "JAVA_HOME not set. Using default java installation ($javaPath)"
else
	JAVA_CMD="$JAVA_HOME"
	echo "Using java from JAVA_HOME variable ($JAVA_HOME)"
	echo
fi

echo "Starting $APP_NAME... press CTRL-C to shutdown"
echo

echo "$JAVA_CMD" $DEBUG_PROP $MEM_PROPS $SERVER_PROPS -jar "$APP"

"$JAVA_CMD" $DEBUG_PROP $MEM_PROPS $SERVER_PROPS -jar "$APP"