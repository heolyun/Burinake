set -e

cd ~/Burinake

echo "Pull latest code..."
git pull --ff-only origin develop

echo "Restart containers..."
docker compose -f docker-compose.dev.yml up -d --build

echo "Deployment complete."
docker ps
