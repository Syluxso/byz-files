# byz-file-service

Object storage **proxy** for Byzantine. Clients upload/download via this API only — MinIO/S3 stays private.

| Setting | Local |
|---------|--------|
| HTTP | `8089` |
| DB | `localhost:5438` / `files` |
| MinIO API | `localhost:9000` |
| MinIO console | `http://localhost:9001` (`minioadmin` / `minioadmin`) |
| JWKS | IAM local `http://localhost:8082/.well-known/jwks.json` |

## Run locally

```bash
# from projects/db
docker compose up -d byz-files-db byz-minio

cd ../file-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Public URL (prod)

There is no dedicated `files.byzantineapp.dev` host yet. Traffic goes through the gateway:

`https://api.byzantineapp.dev/files/api/v1/files/{id}/content`

Never expose raw MinIO URLs to browsers.

## Configure storage (per org)

Admin → Config → Storage, or (platform admin JWT when `BYZ_ADMIN_ORGANIZATION_ID` is set):

```http
POST http://localhost:8089/api/v1/admin/storage-config
Authorization: Bearer <token>
Content-Type: application/json

{
  "organizationId": "<org-uuid>",
  "provider": "minio",
  "credentials": {
    "endpoint": "http://127.0.0.1:9000",
    "region": "us-east-1",
    "bucket": "byz-files",
    "accessKey": "minioadmin",
    "secretKey": "minioadmin",
    "pathStyle": "true"
  }
}
```

Bucket is created automatically if missing.

### Admin gate

Set `BYZ_ADMIN_ORGANIZATION_ID` to the byz-admin org UUID in prod so only that org can call `/api/v1/admin/**`. Leave empty for local (any authenticated JWT).

Also set a real `ENCRYPTION_KEY` (Base64 AES key) in prod — do not use the zeroed default.

## Files API (JWT required; org from token)

| Method | Path | Action |
|--------|------|--------|
| `GET` | `/api/v1/files` | List active files for org |
| `GET` | `/api/v1/files/{id}` | Metadata (includes `checksumSha256`) |
| `POST` | `/api/v1/files` | Multipart upload (`file` part); SHA-256 stored; orphan object cleaned up if DB save fails |
| `GET` | `/api/v1/files/{id}/content` | Stream bytes (proxy) |
| `DELETE` | `/api/v1/files/{id}` | Soft-delete + remove object |

## Admin Files API (platform admin JWT)

| Method | Path | Action |
|--------|------|--------|
| `GET` | `/api/v1/admin/files` | List across orgs (`organizationId?`, `status=active\|deleted\|all`, `page`, `size`) |
| `GET` | `/api/v1/admin/files/{id}` | Metadata (+ `storageKey`) |
| `POST` | `/api/v1/admin/files` | Multipart: `file` + `organizationId` |
| `GET` | `/api/v1/admin/files/{id}/content` | Download stream |
| `PATCH` | `/api/v1/admin/files/{id}` | Rename `{ "name": "…" }` |
| `DELETE` | `/api/v1/admin/files/{id}` | Soft-delete + remove object |

## Kafka

After a successful upload commit, file-service emits `file.created` on topic **`byz.files.file`**
(key = `fileId`). Contract: events-service `docs/EVENTS.md`. Toggle with `BYZ_KAFKA_ENABLED`
(default true). Bootstrap the topic via events-service `POST /api/v1/topics/bootstrap`.

## Deploy

Jenkins → `/opt/services/file-service` → `supervisorctl restart file-service`.

Env sketch: `DB_*`, `IAM_JWKS_URL`, `ENCRYPTION_KEY`, `BYZ_ADMIN_ORGANIZATION_ID`, `KAFKA_BOOTSTRAP`, MinIO reachable from the host.

### One-time server setup (as root)

Jenkins deploy uses `sudo` (same as other Byz services). Create NOPASSWD rules:

```bash
# /etc/sudoers.d/jenkins-deploy-file-service
jenkins ALL=(root) NOPASSWD: /usr/bin/mkdir -p /opt/services/file-service
jenkins ALL=(root) NOPASSWD: /usr/bin/cp /var/lib/jenkins/workspace/byz-files/target/*.jar /opt/services/file-service/app.jar
jenkins ALL=(root) NOPASSWD: /usr/bin/chown root\:root /opt/services/file-service/app.jar
jenkins ALL=(root) NOPASSWD: /usr/bin/supervisorctl reread
jenkins ALL=(root) NOPASSWD: /usr/bin/supervisorctl update file-service
jenkins ALL=(root) NOPASSWD: /usr/bin/supervisorctl restart file-service
```

Also create the supervisor program (once, as root):

```bash
# Or: bash scripts/server-setup.sh  (creates DB + this conf)
cat >/etc/supervisor/conf.d/file-service.conf <<'EOF'
[program:file-service]
directory=/opt/services/file-service
command=/bin/bash -c 'test -f /opt/services/file-service/app.jar && exec /usr/lib/jvm/temurin-23-jdk-amd64/bin/java -jar -Xmx256M /opt/services/file-service/app.jar --server.port=8089 || (echo "app.jar not deployed yet" && sleep 3600)'
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
supervisorctl restart file-service
```

Ensure Postgres DB `files` exists (and MinIO for uploads). Then re-run the Jenkins job.


