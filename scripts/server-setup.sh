#!/bin/bash
# Run once on the Linode as root to stand up file-service under supervisor.
set -euo pipefail

JAVA_BIN="${JAVA_BIN:-/usr/lib/jvm/temurin-23-jdk-amd64/bin/java}"
DEPLOY_DIR=/opt/services/file-service

mkdir -p "$DEPLOY_DIR"

echo "=== Postgres role + database (files) ==="
sudo -u postgres psql <<'SQL'
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'files') THEN
    CREATE ROLE files WITH LOGIN PASSWORD 'files';
  END IF;
END $$;
SELECT 'CREATE DATABASE files OWNER files'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'files')\gexec
GRANT ALL PRIVILEGES ON DATABASE files TO files;
SQL
sudo -u postgres psql -d files -c "GRANT ALL ON SCHEMA public TO files;"

echo "=== Supervisor config ==="
# Adjust DB_PASS / ENCRYPTION_KEY / BYZ_ADMIN_ORGANIZATION_ID for prod.
cat > /etc/supervisor/conf.d/file-service.conf <<EOF
[program:file-service]
directory=${DEPLOY_DIR}
command=/bin/bash -c 'test -f ${DEPLOY_DIR}/app.jar && exec ${JAVA_BIN} -jar -Xmx256M ${DEPLOY_DIR}/app.jar --server.port=8089 || (echo "app.jar not deployed yet" && sleep 3600)'
user=root
autostart=true
autorestart=true
startsecs=15
environment=JAVA_HOME="/usr/lib/jvm/temurin-23-jdk-amd64",DB_URL="jdbc:postgresql://127.0.0.1:5432/files",DB_USER="files",DB_PASS="files",IAM_JWKS_URL="http://127.0.0.1:8082/.well-known/jwks.json",ENCRYPTION_KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",BYZ_ADMIN_ORGANIZATION_ID=""
stdout_logfile=/var/log/supervisor/file-service.log
stderr_logfile=/var/log/supervisor/file-service.err.log
EOF

supervisorctl reread
supervisorctl update file-service
supervisorctl restart file-service || supervisorctl start file-service

echo "Done."
echo "  Health: curl -s http://127.0.0.1:8089/actuator/health"
echo "  Replace ENCRYPTION_KEY and set BYZ_ADMIN_ORGANIZATION_ID before prod use."
echo "  MinIO must be reachable for uploads (configure via Admin → Storage)."
