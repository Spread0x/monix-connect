#!/usr/bin/env bash

set -e

CURRENT_DIR=$(pwd)
echo "CURRENT_DIR=$CURRENT_DIR"

docker-compose -f ./docker-compose.yml stop localstack
docker-compose -f ./docker-compose.yml rm -f localstack
docker-compose -f ./docker-compose.yml up -d localstack

echo -e "Docker ps."
docker ps
