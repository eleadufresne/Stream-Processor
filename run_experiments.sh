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
PROMETHEUS_PID=$!
echo $PROMETHEUS_PID

# start grafana @localhost:3000
echo "Starting Grafana server at localhost:3000"
sudo service grafana-server start

# start a flink cluster @localhost:8081
echo "Starting Flink cluster at localhost:8081"
$FLINK_HOME/bin/start-cluster.sh

# run the scripts
./scripts/100000-line-XP.sh 0
./scripts/10-batch-10000-line-XP.sh 0
./scripts/large-input-images.sh 0

echo "DONE!"

# stop prometheus
kill $PROMETHEUS_PID

# stop grafana
sudo systemctl stop grafana-server

# stop the cluster
$FLINK_HOME/bin/stop-cluster.sh