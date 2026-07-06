set -e

cd ~/Burinake

echo "Log in to Azure Container Registry..."
echo "$ACR_PASSWORD" | docker login "$ACR_LOGIN_SERVER" -u "$ACR_USERNAME" --password-stdin

echo "Pull latest images from ACR..."
docker compose -f docker-compose.prod.yml pull

echo "Start services without rebuilding on the VM..."
docker compose -f docker-compose.prod.yml up -d

echo "Deployment complete."
docker ps
