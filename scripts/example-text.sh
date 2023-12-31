#!/bin/bash

echo "Running example 2: processing a text files"

# clear previous data
rm -f orchard-watch/data/*.txt

# should we start a new cluster? (true by default)
MANAGE_CLUSTER=${1:-1}
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/start-cluster.sh
fi

# start monitoring the directory (--detached will make the command return after the submission is done)
OUTPUT="$($FLINK_HOME/bin/flink run --detached target/fruit*.jar --mode TEXT --job "text example" | tee /dev/tty)"
JOB_ID=$(echo "$OUTPUT" | awk 'END{print $NF}')

# copy tree files every 10 seconds
for i in {0..4}; do
    sleep 10s
    cp orchard-watch/sample-input/tree_${i}.txt orchard-watch/data/
    echo "processing tree_${i}.txt..."
done
# ensure the job completed
sleep 21s
echo "done!"

# terminate the job
$FLINK_HOME/bin/flink stop --savepointPath $FLINK_HOME/tmp/flink-savepoints $JOB_ID

# stop the cluster if management true
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/stop-cluster.sh
fi