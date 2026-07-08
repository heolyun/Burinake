# Azure Blob Storage Setup

## Purpose

Burinake uses Azure Blob Storage for uploaded image and file assets such as CCTV snapshots, fire event evidence, and generated reports.

Docker images are stored in Azure Container Registry. Blob Storage is for application files, not container images.

## Azure Resources

- Storage Account: `burinakestorage`
- Blob Container: `fire-events`

## Required Environment Variables

- `AZURE_STORAGE_CONNECTION_STRING`
- `AZURE_BLOB_CONTAINER=fire-events`

The connection string must not be committed to Git. Store it in GitHub Secrets, Azure VM environment variables, or a local `.env` file.

## Blob Folder Layout

Files are stored under the configured container with these logical prefixes:

```text
snapshots/YYYY/MM/DD/{imageId}/original.{ext}
fire-events/YYYY/MM/DD/{imageId}/evidence.{ext}
reports/YYYY/MM/DD/{reportId}/report.{ext}
```

The backend currently uses `snapshots/...` for uploaded detection images. `fire-events/...` and `reports/...` are reserved for event evidence and generated report files.

## Backend Behavior

The backend uses the `ImageStorageService` interface.

- `AzureBlobImageStorageService`: used when `AZURE_STORAGE_CONNECTION_STRING` is set
- `LocalImageStorageService`: used when `AZURE_STORAGE_CONNECTION_STRING` is not set

Azure upload success returns:

- Blob path, such as `snapshots/2026/07/08/123/original.jpg`
- Blob URL from Azure SDK
- Storage provider value `AZURE_BLOB`
- Container name

Azure upload failure is logged and raised as an exception. The Azure implementation does not silently fall back to local storage.

## Docker Compose

Development:

`docker-compose.dev.yml` passes `AZURE_STORAGE_CONNECTION_STRING` and `AZURE_BLOB_CONTAINER` from local environment or `.env`.

Production:

`docker-compose.prod.yml` passes the same variables to the backend container. GitHub Actions exports these values from repository secrets before running `scripts/deploy.sh`.

## GitHub Secrets

Add these secrets:

- `AZURE_STORAGE_CONNECTION_STRING`
- `AZURE_BLOB_CONTAINER`

`AZURE_BLOB_CONTAINER` should be set to `fire-events`.
