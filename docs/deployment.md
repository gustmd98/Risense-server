# Docker Hub / Oracle deployment

GitHub Actions runs tests on pull requests. After merge to main it runs tests,
then builds amd64/arm64 images and publishes latest and sha-<full-commit-sha> tags.
Oracle only pulls and runs the image. PostgreSQL remains on Supabase.

## GitHub repository settings

Settings > Secrets and variables > Actions:

- Secret DOCKERHUB_USERNAME: Docker Hub login that can push to the image repository.
- Secret DOCKERHUB_TOKEN: that login's Docker Hub access token with write permission.
- Variable DOCKERHUB_IMAGE: image repository, e.g. mlr0/risense-server, once created.

The login can differ from the repository owner if it has collaborator access.
Do not put DB credentials, JWT secrets or registry tokens into source files.

## Server preparation

SSH into the server and check that Docker and Compose are installed:

```sh
ssh gustmd98@168.138.213.117
uname -m
docker --version
docker compose version
```

Use a separate deployment directory containing docker-compose.yml and .env.
Copy .env.example to .env and fill in DB_PASSWORD, JWT_SECRET and the confirmed
DOCKER_IMAGE. Use the same JWT_SECRET across restarts. Set file permissions:

```sh
chmod 600 .env
```

If the registry repository is private, run docker login with an account allowed
to pull. Keep credentials out of chat, Git and shell command arguments.
Ensure server firewall and Oracle network rules allow the intended API access.

## Release

Wait for the main-branch Docker image workflow to succeed. In the deployment directory:

```sh
docker compose pull
docker compose up -d
docker compose ps
docker compose logs --tail=100 app
curl -f http://localhost:8080/api/health
```

The container is limited to 640 MiB and the JVM heap to 60% of the container limit.
Check actual server memory use before adjusting. Logs rotate to limit disk usage.

For a reproducible release set IMAGE_TAG=sha-<full-commit-sha> in .env.
To roll back, set the previous sha tag, then pull and run up -d again.
DB migrations need separate compatibility review; rolling back an image does not
undo Flyway migrations. Do not run Flyway clean or edit applied V1/V2 files.

## Validation status

Configuration and whitespace reviewed locally. Docker is unavailable in the editing
runtime, so image build and Compose execution must be verified in CI and on the server.
Deployment has not been performed by this change.
