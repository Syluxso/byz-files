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

## Deploy

Jenkins → `/opt/services/file-service` → `supervisorctl restart file-service`.

Env sketch: `DB_*`, `IAM_JWKS_URL`, `ENCRYPTION_KEY`, `BYZ_ADMIN_ORGANIZATION_ID`, MinIO reachable from the host.
