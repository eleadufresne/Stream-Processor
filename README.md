# Overview
 This is a windowed stream processing for monitoring fruit tree images from a directory.

# Requirements
You will need to clone this repository into the home directory of your UNIX-like systems, including Linux, Mac OS X, 
and [Cygwin](https://cygwin.com/install.html) or WSL (for Windows) and ensure that Apache Flink is installed.
```shell
git clone https://github.com/eleadufresne/fruit-stream-processor.git
```
To run Flink, it's essential to have [Java 11](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html) installed 
on your system. You can verify the installed version by executing `$ java -version` in your command prompt. Follow this 
[tutorial](https://nightlies.apache.org/flink/flink-docs-stable/docs/try-flink/local_installation/#first-steps) to get 
started with Flink. 🙃 You might need to install the latest [Hadoop](https://hadoop.apache.org/releases.html) binaries as well.

# Configuration
Please keep in mind the following steps when setting up WSL. If you're using a different environment, you may need 
to make some additional configurations.  

# variables and permissions

As a first step, let's create an environment variable for the flink installation directory.
The command should look something like that.
```shell
export FLINK_HOME=/mnt/c/Projects/Apache/flink-1.15.2
```

Ensure that you have enough permissions to use the repository.
```shell
chmod -R 777 $FLINK_HOME
```

Next, navigate to the Flink installation directory, open the ``flink-conf.yaml`` file and add the following line to the end of the file:

```shell
taskmanager.resource-id: stream-processing-logs
```
Flink will create a temporary folder with the given name to maintain its state logs as it sets up a cluster.

## database 
To create the database, you need to make sure that MySQL is installed in your system. If not, run the following commands
before proceeding.

```shell
sudo apt install mysql-server
sudo service mysql status
sudo ss -tap | grep mysql
sudo service mysql restart
```

## network connection
In your **WSL terminal**, navigate to the `conf` folder of your Flink directory. You may execute the command ``hostname -I`` to find the IP address of your WSL instance. Set the following values in `flink-conf.yaml`.

```yaml
rest.port: 8081
rest.address: localhost
rest.bind-adress: 0.0.0.0
```

# Running the Application

To run the app, you must first create database to store the output. We first need to create a database . Start the MySQL 
server and Log in to MySQL as root, create a new user with privileges to access all databases. Then, log out of MySQL 
and log back in as the newly created user. Finally, create a new database with appropriate schema (a table named 
pears with columns feature and count)

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
$FLINK_HOME/bin/start-cluster.sh
# generate a JAR file for the Flink job
cd $FRUIT_DIR && mvn clean package && cd ~
# start monitoring the files in the "data" directory
$FLINK_HOME/bin/flink run $FRUIT_DIR/tar*/fruit*.jar --path $FRUIT_DIR/data/
```

To access the Flink dashboard, go to [localhost:8081](http://localhost:8081) in your browser. If you're using WSL, 
you might need to replace "localhost" with the IP address of your WSL instance. Use the command ``hostname -I`` 
to find the IP address. Then, go to http://[WSL-IP-ADDRESS]:8081 in your browser.

When you are done, stop the cluster with`$FLINK_HOME/bin/stop-cluster.sh` and shut down the MySQL server with 
``mysqladmin --user root shutdown``.

# Troubleshooting

## `java.nio.file.FileSystemException`

If you encounter a `java.nio.file.FileSystemException`, it could mean that the task-executor wasn't stopped properly. 
To get around this issue, you can use the Resource Monitor on Windows to find out which process is using the file:

1. Open the Task Manager (Ctrl + Shift + Esc).
2. Go to the "Performance" tab.
3. Click on "Open Resource Monitor" at the bottom.
4. In the Resource Monitor, navigate to the "CPU" tab.
5. Expand the "Associated Handles" section.
6. Type in the name of the file you're looking for in the search box.
7. Terminate any processes that are using the file.

**Alternatively**, you may type `jsp` in the terminal to show all running instances. Run `$FLINK_HOME/bin/stop-cluster.sh` 
until only `JSP` remains.   

## connection issues on WSL
If you're using WSL, you might need to add a rule to open up ports for Flink since WSL runs on its own IP address.
In the **Windows PowerShell**, execute the following to create a new rule.

```shell
# open ports 1000-60000 (feels free to make it stricter)
New-NetFireWallRule -Profile Private -DisplayName 'Open ports for Flink on WSL' 
    -Direction Inbound -LocalPort 1000-60000 -Action Allow -Protocol TCP
# restart wsl to apply changes
wsl --shutdown
wsl
```

In your **WSL terminal**, you may open all -or specific- ports using the following commands.

```shell
# allow all incoming traffic
sudo ufw default allow incoming
# allow all outgoing traffic
sudo ufw default allow outgoing
# allosw all tcp ports
sudo ufw allow proto tcp to any port 1:65535
# allow the SSH port 22
sudo ufw allow ssh
# apply the changes
sudo ufw reload
# enable the firewall
sudo ufw enable
# check the status
sudo ufw status verbose
```

You may also need to set up SSH if it's not already done.
```shell
# install SSH server
sudo apt update
sudo apt install openssh-server
# start SSH service
sudo systemctl start ssh
sudo systemctl enable ssh
# check SSH service status
sudo systemctl status ssh
```
You can edit the configuration file located at ``/etc/ssh/sshd_config`` if necessary. Restart the SSH service to 
apply the changes with ``sudo systemctl restart ssh``.

