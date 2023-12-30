#!/bin/bash

# build a new JAR (true by default)
BUILD_JAR=${1:-1}
if [ "$BUILD_JAR" -eq 1 ]; then
    mvn clean package
fi

echo "Running all experiments..."

# start prometheus @localhost:9090
echo "Starting Prometheus at localhost:9090"
nohup bash -c "prometheus --web.page-title='Fruit Stream Processing' --config.file=./config/prometheus.yml" &

# start grafana @localhost:3000
echo "Starting Grafana server at localhost:3000"
sudo service grafana-server start
sudo systemctl enable grafana-server.service

# start a flink cluster @localhost:8081
echo "Starting Prometheus at localhost:9090"
$FLINK_HOME/bin/start-cluster.sh

# run the scripts
./scripts/large-input-images.sh 0
#./scripts/large-input-text.sh 0

# Wait before stopping services
read -p -r "\nPlease press any key to stop the services and exit.\n"

# stop prometheus
kill $PROMETHEUS_PID

# stop grafana
sudo systemctl stop grafana-server

# stop the cluster
$FLINK_HOME/bin/stop-cluster.sh