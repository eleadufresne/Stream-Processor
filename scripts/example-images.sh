#!/bin/bash

echo "Running example 1: processing an image"

# clear previous data
rm -f orchard-watch/data/*.txt
rm -f orchard-watch/output/processed-images/*

# should we start a new cluster? (true by default)
MANAGE_CLUSTER=${1:-1}
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/start-cluster.sh
fi

# start monitoring the directory (--detached will make the command return after the submission is done)
OUTPUT="$($FLINK_HOME/bin/flink run --detached target/fruit*.jar --mode IMAGES --job "image example" | tee /dev/tty)"
JOB_ID=$(echo "$OUTPUT" | awk 'END{print $NF}')

# copy the single image file
cp orchard-watch/sample-input/billiard_0.txt orchard-watch/data/
echo "processing billiard_0.txt..."

# ensure the job completed
sleep 21

# terminate the job
$FLINK_HOME/bin/flink stop --savepointPath $FLINK_HOME/tmp/flink-savepoints $JOB_ID
echo "done!"

# stop the cluster if management true
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/stop-cluster.sh
fi