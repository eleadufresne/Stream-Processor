#!/bin/bash

echo "Running experiment: processing batches of 10 text files (10 000 lines each)"

# clear previous data
rm -f orchard-watch/data/*.txt
rm -r orchard-watch/tmp

## generate large files in the a temporary directory
echo "Generating temporary files..."
tmp="orchard-watch/tmp"
mkdir $tmp
words=("ripe orange" "immature orange" "leaf" "branch" "rotten orange")
for i in {0..100}; do
    file="large_tree_${i}.txt"
    echo "generating ${file}..."
    for (( j = 0; j < 10000; j++ )); do # 10 000 lines per file
        echo "${words[$RANDOM % ${#words[@]}]}" >> "${tmp}/${file}"
    done
done

# should we start a new cluster? (true by default)
MANAGE_CLUSTER=${1:-1}
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/start-cluster.sh
fi

# start monitoring the directory (--detached will make the command return after the submission is done)
OUTPUT="$($FLINK_HOME/bin/flink run --detached target/fruit*.jar --mode TEXT --job "large input text" | tee /dev/tty)"
JOB_ID=$(echo "$OUTPUT" | awk 'END{print $NF}')

# move the files to the data directory
for (( j = 0; j < 10; j++ )); do
  sleep 10s
  offset=$((j*10))
  for i in {0..10}; do
      k=$((i+offset))
      file="large_tree_${k}.txt"
      cp "${tmp}/${file}" "orchard-watch/data/large_tree_${j}_${k}.txt"
      echo "processing ${file}..."
  done
done

echo "sleeping"
sleep 5m

# ensure the job completed
sleep 21s
echo "done!"

# terminate the job
#$FLINK_HOME/bin/flink stop --savepointPath $FLINK_HOME/tmp/flink-savepoints $JOB_ID

# delete the tmp folder
#rm -r $tmp

# stop the cluster if management true
if [ "$MANAGE_CLUSTER" -eq 1 ]; then
    $FLINK_HOME/bin/stop-cluster.sh
fi