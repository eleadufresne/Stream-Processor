# Overview
 This is a windowed stream processing for monitoring fruit tree images from a directory.

# Requirements
You will need to clone this repository into the home directory of your UNIX-like systems, including Linux, Mac OS X, 
and [Cygwin](https://cygwin.com/install.html) (for Windows) and ensure that Apache Flink is installed.
```shell
git clone https://github.com/eleadufresne/fruit-stream-processor.git
```
Flink is compatible with all To run Flink, it's essential to have [Java 11](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html) installed 
on your system. You can verify the installed version by executing `$ java -version` in your command prompt. Follow this 
[tutorial](https://nightlies.apache.org/flink/flink-docs-stable/docs/try-flink/local_installation/#first-steps) to get 
started with Flink. 🙃

# Configuration
Please keep in mind the following steps when setting up Cygwin. If you're using a different environment, you may need 
to make some additional configurations. 

To begin with, ensure that you've installed the latest Flink distribution, mintty and netcat. Then, navigate to the 
Cygwin folder and locate the ```.bash_profile``` file in the home directory. Add the two lines provided below to the 
end of the file and save it:

```shell
export SHELLOPTS
set -o igncr
```
These commands ensure proper handling of command line arguments in all child processes and normal behavior of the 
carriage return when executing a command in Cygwin.

Next, download the latest Flink distribution and open the flink-conf.yaml file. Add the following line to the end of the file:

```shell
taskmanager.resource-id: stream-processing-logs
```
Flink will create a temporary folder under Cygwin with the given name to maintain its state logs as it sets up a cluster.

# Running the Application
From the root directory of your UNIX-like system, execute the following commands.

```shell
# start a cluster
cd flink* && ./bin/start-cluster.sh
# generate a JAR file for the Flink job
cd ../fruit* && mvn clean package && cd ../flink* 
# start monitoring the files in the "data" directory
./bin/flink run ../fruit*/tar*/fruit*.jar --path data/
```
Some example files are provided in the util folder for monitoring. You now visualize the job on the Flink dashboard 
on [localhost:8081](http://localhost:8081).