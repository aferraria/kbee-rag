#!/bin/bash
#
# start-service.sh - entry point intended for use by a service manager
#                    (e.g. systemd) in the future. Not used for manual
#                    start/stop; use start.sh / start-cmd.sh / shutdown.sh.
#
# SERVER_HOME may be provided by the service environment; if not, it is
# derived from this script's location.

export SERVER_HOME="${SERVER_HOME:-$(cd "$(dirname $(readlink -f "$0"))/..";pwd)}"
source $SERVER_HOME/bin/config.sh

rm -f $SERVER_HOME/logs/startup.log  2> /dev/null

cd "$SERVER_HOME"

if [ -z "$JAVA_HOME" ]; then
	JAVA_CMD="java"
	javaPath=$(readlink -nf $(which java) | xargs dirname | xargs dirname | xargs dirname)
	echo "JAVA_HOME not set. Using default java installation ($javaPath)"
else
	JAVA_CMD="$JAVA_HOME"
	echo "Using java from JAVA_HOME variable ($JAVA_HOME)"
fi

exec "$JAVA_CMD" $DEBUG_PROP $MEM_PROPS $SERVER_PROPS -jar "$APP"