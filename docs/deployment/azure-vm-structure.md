# Azure VM Directory Structure

Recommended VM layout:

```text
/home/<deploy-user>/Burinake/          # Git repository clone
|-- .github/
|-- ai/
|-- backend/
|-- frontend/
|-- infra/
|-- docker-compose.dev.yml
|-- docker-compose.prod.yml
`-- .env

/opt/burinake/
|-- models/
|   |-- yolo/
|   `-- vlm/
|-- logs/
`-- backups/
```

## Notes

- `/home/<deploy-user>/Burinake` is updated by `git pull`.
- `/opt/burinake/models` is persistent application data and must remain outside the repository.
- Docker Compose mounts `/opt/burinake/models/yolo` to `/app/models` in the YOLO container.
- Docker Compose mounts `/opt/burinake/models/vlm` to `/app/models` in the VLM container.
- Model directories should be owned by the deploy user or a shared Docker-accessible group.
