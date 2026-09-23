#!/bin/bash

export SERVER_HOME=$(cd "$(dirname $(readlink -f "$0"))/..";pwd)
source $SERVER_HOME/bin/config.sh

pid=$(pgrep -f "[j]ava.*$APP" | head -n 1)

if [[ -z "$pid" ]]
then
	echo "$APP_NAME is not running."
	exit 0
fi

echo "Stopping $APP_NAME (pid $pid) ..."

# graceful shutdown first (SIGTERM lets Spring shutdown hooks run)
kill "$pid"

# wait up to 20 seconds
for i in $(seq 1 20); do
	if ! kill -0 "$pid" 2>/dev/null; then
		echo "$APP_NAME stopped."
		exit 0
	fi
	sleep 1
done

echo "Process did not stop gracefully, forcing kill -9 ..."
kill -9 "$pid"
echo "$APP_NAME killed."
