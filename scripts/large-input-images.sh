#!/bin/bash

echo "Running experiment 1: processing images in large input"

# clear previous data
rm -f orchard-watch/data/*.txt
rm -f orchard-watch/output/processed-images/*

# should we start a new cluster? (true by default)
MANAGE_CLUSTER=${1:-1}
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/start-cluster.sh
fi

# start monitoring the directory (--detached will make the command return after the submission is done)
OUTPUT="$($FLINK_HOME/bin/flink run --detached target/fruit*.jar --mode IMAGES --job "large input images" | tee /dev/tty)"
JOB_ID=$(echo "$OUTPUT" | awk 'END{print $NF}')
sleep 10s

# parse the different types or circular object images by batches of 4
for (( k = 1; k < 10; k++ )); do
  for i in $(seq 1 4 77); do
    sleep 10s
    j=$((i + 3))
    cp orchard-watch/sample-input/billiard_${i}-${j}.txt orchard-watch/data/
    echo "processing billiard balls images ${i} through ${j}"
  done
  for i in $(seq 1 4 45); do
    sleep 10s
    j=$((i + 3))
    cp orchard-watch/sample-input/tennis_${i}-${j}.txt orchard-watch/data/
    echo "processing tennis balls images ${i} through ${j}"
  done
done

# ensure the job completed
sleep 21s

# terminate the job
$FLINK_HOME/bin/flink stop --savepointPath $FLINK_HOME/tmp/flink-savepoints $JOB_ID
echo "done!"

# stop the cluster if management true
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/stop-cluster.sh
fi