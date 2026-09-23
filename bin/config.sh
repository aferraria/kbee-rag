#!/bin/bash
#
# ---------------------------------------------------------------------------
# config.sh - the ONLY file you need to edit to reuse these scripts
#             for another Spring Boot application.
#
# Expected installation layout:
#   $SERVER_HOME/
#     app/       -> the application fat jar ($APP_NAME*.jar)
#     bin/       -> these scripts
#     config/    -> application.yml (overrides bundled defaults), log4j2.xml
#     logs/      -> log files (startup.log, ...)
#     tmp/       -> work directory
#
# SERVER_HOME is set by the calling scripts (check.sh, start.sh, etc.)
# ---------------------------------------------------------------------------

# --- Application name -------------------------------------------------------
# Used to find the jar in $SERVER_HOME/app and to identify the running process.
# If left empty, it is auto-detected from the first *.jar found in app/.
APP_NAME="${APP_NAME:-}"

# --- User allowed to run the application ------------------------------------
APP_USER="${APP_USER:-kbee}"

# --- Java --------------------------------------------------------------------
# JAVA_HOME must point to the java executable (or leave unset to use PATH).
# To find java on most Linux systems:  readlink -f $(which java)
export JAVA_HOME="${JAVA_HOME:-$(readlink -f $(which java))}"

# --- JVM memory --------------------------------------------------------------
export MEM_PROPS="-Xms1G -Xmx4G"

# --- Locate the application jar ----------------------------------------------
app_dir="$SERVER_HOME/app"

if [ -n "$APP_NAME" ]; then
    jar_file=$(find "$app_dir" -maxdepth 1 -type f -name "$APP_NAME*.jar" 2>/dev/null | head -n 1)
else
    jar_file=$(find "$app_dir" -maxdepth 1 -type f -name "*.jar" 2>/dev/null | head -n 1)
fi

if [ -n "$jar_file" ]; then
    export APP="$jar_file"
    # derive APP_NAME from the jar file name if not set (strip version/extension)
    if [ -z "$APP_NAME" ]; then
        APP_NAME=$(basename "$jar_file" .jar)
    fi
else
    echo "No application jar found in '$app_dir'"
fi

export APP_NAME

# --- JVM system properties ---------------------------------------------------
# spring.config.additional-location makes $SERVER_HOME/config/application.yml
# override (property by property) the application.yml bundled inside the jar.
export SERVER_PROPS="
-Dspring.config.additional-location=optional:file:$SERVER_HOME/config/
-Dwork=$SERVER_HOME/tmp/
-Dlog-path=$SERVER_HOME/logs
-Dlog4j.configurationFile=$SERVER_HOME/config/log4j2.xml
-DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
-Djava.net.preferIPv4Stack=true
-Dfile.encoding=UTF-8
-Dsun.jnu.encoding=UTF-8"

# --- Debug (e.g. remote debugging) -------------------------------------------
# export DEBUG_PROP="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
export DEBUG_PROP=""