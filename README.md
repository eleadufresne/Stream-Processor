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

You might need to install the latest [Hadoop](https://hadoop.apache.org/releases.html) binaries as well.

# Configuration
Please keep in mind the following steps when setting up Ubuntu. If you're using a different environment, you may need 
to make some additional configurations.  

## Permissions
Give all permissions.

```shell
chmod -R flink-*
```

## Environment

To begin with, ensure that you've installed the latest Flink distribution. Then, navigate to the 
home directory of you environment and locate the ``.bash_profile`` or ``.profile`` file. Add the two lines provided below to the 
end of the file and save it:

```shell
export SHELLOPTS
```

These commands ensure proper handling of command line arguments in all child processes and normal behavior of the 
carriage return when executing a command.

Next, download the latest Flink distribution and open the flink-conf.yaml file. Add the following line to the end of the file:

```shell
taskmanager.resource-id: stream-processing-logs
```
Flink will create a temporary folder with the given name to maintain its state logs as it sets up a cluster.


## Database
To create the database, you need to make sure that MySQL is installed in your system. If not, follow these instructions
before proceeding.

```shell
sudo apt install mysql-server

sudo service mysql status
sudo ss -tap | grep mysql
sudo service mysql restart
```

## Port
If you're using WSL open up the port for flink.

```shell
New-NetFireWallRule -Profile Private -DisplayName 'Open port for Flink dashboard' `
    -Direction Inbound -LocalPort 8081 -Action Allow -Protocol TCP
# restart wsl to apply changes
wsl --shutdown
wsl
```

Next, open the `flink-conf.yaml` file and replace "localhost" with the IP address of your WSL instance. Use the command 
``hostname -I`` to find the IP address.


# Running the Application

To run the app, you must first create database to store the output. We first need to create a database . Start the MySQL 
server and Log in to MySQL as root, create a new user with privileges to access all databases. Then, log out of MySQL 
and log back in as the newly created user. Finally, create a new database with appropriate schema (a table named 
oranges with columns feature and count)

```mysql
# start the MySQL server
sudo service mysql status
# log into MySQL as root
sudo mysql -u root
# create a new db user
CREATE USER 'fruit_enthusiast'@'localhost' IDENTIFIED BY 'Fru!t5';
GRANT ALL PRIVILEGES ON *.* TO 'fruit_enthusiast'@'localhost';
FLUSH PRIVILEGES;
# log out of MySQL
quit
# log in as the newly created user, and type in the password (oranges)
mysql -u fruit_enthusiast -p
# create a new database
CREATE DATABASE fruits;
# create a table inside the database
USE fruits
CREATE TABLE pears
(
    feature         VARCHAR(150) NOT NULL,               # feature of the orange (ripe, rotten, etc.)
    count           INT unsigned NOT NULL,               # number of such oranges
    PRIMARY KEY     (feature)                            # make the feature the primary key
);
```

From the root directory of your UNIX-like system, execute the following commands.

```shell
# start a cluster
cd flink* && ./bin/start-cluster.sh
# generate a JAR file for the Flink job
cd ../fruit* && mvn clean package && cd ../flink* 
# start monitoring the files in the "data" directory
./bin/flink run ../fruit*/tar*/fruit*.jar --path data/
```
To access the Flink dashboard, go to [localhost:8081](http://localhost:8081) in your browser. If you're using WSL, 
replace "localhost" with the IP address of your WSL instance. Use the command ``hostname -I`` to find the IP address. 
Then, go to http://[WSL-IP-ADDRESS]:8081 in your browser.

Some example files are provided in the util folder for monitoring. You now visualize the job on the Flink dashboard on .

When you are done, stop the cluster with`./bin/stop-cluster.sh` and shut down the MySQL server with 
``mysqladmin --user root shutdown``.

# Common Issues

If you encounter a `java.nio.file.FileSystemException`, it could mean that the task-executor wasn't stopped properly. 
To get around this issue, you can use the Resource Monitor on Windows to find out which process is using the file:

1. Open the Task Manager (Ctrl + Shift + Esc).
2. Go to the "Performance" tab.
3. Click on "Open Resource Monitor" at the bottom.
4. In the Resource Monitor, navigate to the "CPU" tab.
5. Expand the "Associated Handles" section.
6. Type in the name of the file you're looking for in the search box.
7. Terminate any processes that are using the file.


```shell
$FLINK_HOME/bin/start-cluster.sh
cd $FRUIT_DIR && mvn clean package && cd ~
$FLINK_HOME/bin/flink run $FRUIT_DIR/tar*/fruit*.jar
```

https://stackoverflow.com/questions/70736171/cannot-access-flink-dashboard-localhost8081-on-windows
have the following versions: java 11.0.16 and flink 1.15.2.

sudo apt-get update
sudo apt install openjdk-11-jre-headless
export FLINK_HOME=/mnt/c/Projects/Apache/flink-1.15.2

I set the following in flink-conf.yaml

rest.port: 8081
rest.address: localhost
rest.bind-adress: 0.0.0.0

Whereby I changed the bind address for localhost to 0.0.0.0 this seems to have fixed the problem.

$FLINK_HOME/bin/start-cluster.sh


