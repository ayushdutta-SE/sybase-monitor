# Sybase Monitor

Spring Boot collector + API, React dashboard, MySQL for history.

## Run
    cp .env.example .env     # fill in values; MONITOR_ENC_KEY = `openssl rand -base64 32`
    docker compose --env-file .env up --build
    open http://localhost:8080   (login = ADMIN_USER / ADMIN_PASSWORD)

## Sybase-side setup (once per monitored ASE)
    create login sybmon with password '***'
    go
    grant role mon_role to sybmon
    go
    sp_configure 'enable monitoring', 1     -- required for cache metrics (MDA tables)
    go
The login needs read access to master (default). No write permissions are required.
