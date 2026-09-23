# Application management scripts (Ubuntu Linux)

Generic scripts to manually start / stop / check a Spring Boot application.
They are **app-agnostic**: to reuse them in another application, copy the
`bin/` directory and (optionally) edit only `config.sh`.

## Installation layout

```
$SERVER_HOME/
  app/       -> the application fat jar (only one jar expected)
  bin/       -> these scripts
  config/    -> application.yml (overrides jar defaults), log4j2.xml
  logs/      -> log files (startup.log, ...)
  tmp/       -> work directory
```

## Scripts

| Script            | Purpose                                                        |
|-------------------|----------------------------------------------------------------|
| `config.sh`       | Single point of configuration (app name, user, JVM options).   |
| `start-cmd.sh`    | Starts the app in the foreground; CTRL-C stops it.             |
| `start.sh`        | Starts the app in the background (nohup) and tails startup.log. Use `start.sh noTail` to skip log output. |
| `shutdown.sh`     | Graceful stop (SIGTERM, 20s timeout, then kill -9).            |
| `check.sh`        | Shows whether the app is running and its pid.                  |
| `start-service.sh`| Entry point for a future systemd unit (not used manually).     |
| `findjava.sh`     | Shows the location of the java installation.                   |

## Reusing in another application

The jar name is **auto-detected** (first `*.jar` in `app/`), so usually
no changes are needed. In `config.sh` you can optionally adjust:

- `APP_NAME`  - explicit jar name prefix (default: auto-detect)
- `APP_USER`  - OS user allowed to run the app (default: `kbee`)
- `MEM_PROPS` - JVM heap settings (default: `-Xms1G -Xmx4G`)
- `SERVER_PROPS` / `DEBUG_PROP` - extra JVM system properties

## Configuration override

Dev defaults live in the jar (`src/main/resources/application.yml`).
In testing/production, put only the differing properties in
`$SERVER_HOME/config/application.yml`; it overrides the bundled file
property by property via `spring.config.additional-location`.
