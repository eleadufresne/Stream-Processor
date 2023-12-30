#!/bin/bash

echo "Running experiment 2: processing text files in large input"

# clear previous data
rm -f orchard-watch/data/*.txt

# should we start a new cluster? (true by default)
MANAGE_CLUSTER=${1:-1}
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/start-cluster.sh
fi

# start monitoring the directory (--detached will make the command return after the submission is done)
OUTPUT="$($FLINK_HOME/bin/flink run --detached target/fruit*.jar --mode TEXT --job "large input text" | tee /dev/tty)"
JOB_ID=$(echo "$OUTPUT" | awk 'END{print $NF}')

# generate large files in the data directory
words=("ripe orange" "immature orange" "leaf" "branch" "rotten orange")
for i in {0..20}; do
    file_name="large_tree_${i}.txt"
    for (( j = 0; j < 1000; j++ )); do
        echo "${words[$RANDOM % ${#words[@]}]}" >> "orchard-watch/data/$file_name"
    done
    sleep 10
done
# ensure the job completed
sleep 11
echo "done!"

# terminate the job
$FLINK_HOME/bin/flink stop --savepointPath $FLINK_HOME/tmp/flink-savepoints $JOB_ID

# stop the cluster if management true
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/stop-cluster.sh
fi