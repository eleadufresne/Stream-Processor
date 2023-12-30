#!/bin/bash

# should we build a new JAR? (true by default)
BUILD_JAR=${1:-1}
if [ "$BUILD_JAR" -eq 1 ]; then
    mvn clean package
fi

echo "Running all experiments..."

# start a cluster
$FLINK_HOME/bin/start-cluster.sh

# run the scripts
./scripts/large-input-images.sh 0
./scripts/large-input-text.sh 0

# stop the cluster
$FLINK_HOME/bin/stop-cluster.sh