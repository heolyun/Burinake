set -e

cd ~/Burinake

echo "Pull latest code..."
git pull origin main

echo "Restart containers..."
docker compose -f docker-compose.dev.yml down
docker compose -f docker-compose.dev.yml up -d --build

echo "Deployment complete."
docker ps
