#!/bin/bash

RED="\e[31m"
GREEN="\e[32m"
ENDCOLOR="\e[0m"

export SERVER_HOME=$(cd "$(dirname $(readlink -f "$0"))/..";pwd)

source $SERVER_HOME/bin/config.sh

# find the running process by the full jar path (agnostic, avoids
# matching other java apps on the same machine)
pid=$(pgrep -f "[j]ava.*$APP" | head -n 1)

if [[ ! -z "$pid" ]]
then
		echo -e "$APP_NAME running on pid ${GREEN}$pid${ENDCOLOR}"
else
		echo -e "$APP_NAME ${RED}not running${ENDCOLOR}"
fi