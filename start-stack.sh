#!/usr/bin/env bash
set -e
echo "Starting containers..."
docker compose up -d
echo "Waiting for Kafka healthcheck..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' kafka 2>/dev/null)" = "healthy" ]; do
  sleep 2
done
echo "Applying Codespaces inter-container networking fix (non-persistent, needed after every fresh VM boot)..."
sudo iptables-legacy -I DOCKER-USER -i br-+ -o br-+ -j ACCEPT
echo "Stack ready:"
docker compose ps
