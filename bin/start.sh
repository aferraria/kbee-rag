#!/bin/bash
#
# start.sh - starts the application in the background (nohup) and tails
#            the startup log. Use "start.sh noTail" to skip log output.
#            To run in foreground (ctrl-c to stop) use start-cmd.sh.

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

rm -f $SERVER_HOME/logs/startup.log  2> /dev/null
nohup $SERVER_HOME/bin/start-cmd.sh < /dev/null > /dev/null 2>&1 &


echo "Background process launched."

if ! [ "$1" = "noTail" ]; then
	echo "Appending 100 lines of startup.log to standard output, press ctrl+c to stop printing"
	echo "If no output is generated after 1 minute, try start-cmd.sh"

	sleep 3

	while [ ! -f $SERVER_HOME/logs/startup.log ]
	do
	  echo "waiting for startup.log file to be created."
	  sleep 2
	done

	echo "startup.log created"

	sleep 3

	echo "collecting startup log info"

	sleep 4

	cat $SERVER_HOME/logs/startup.log
fi
