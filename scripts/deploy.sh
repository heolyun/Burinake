set -e

cd ~/Burinake

echo "Update frontend, backend, and postgres only..."
docker compose -f docker-compose.dev.yml up -d --build --no-deps frontend backend postgres

echo "Keep existing AI containers without rebuilding..."
docker compose -f docker-compose.dev.yml up -d --no-build --no-deps yolo-server vlm-server

echo "Deployment complete."
docker ps
