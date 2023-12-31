# Overview
Apache Flink powers this app and offers an efficient solution for intelligent agriculture. It automates data processing 
and continuously monitors a designated directory, reading new files every 10 seconds. The app is explicitly tailored to 
analyze fruit data, effectively filtering and quantifying various fruit types. After processing, it stores these results
in a MySQL database. This tool streamlines agricultural data analysis, significantly reducing manual effort and time. 
It paves the way for more informed and timely decisions in intelligent farming.

### Features
- Real-time monitoring of fruit quality and maturity
- Automated counting and classification
- Database integration for persistent record-keeping

### Table of Contents
<!-- TOC -->
* [Overview](#overview)
    * [Features](#features)
    * [Table of Contents](#table-of-contents)
* [Requirements](#requirements)
* [Installation](#installation)
* [Setup Configuration](#setup-configuration)
* [Running the Application](#running-the-application)
    * [Launching the Application](#launching-the-application)
    * [Accessing the Flink Dashboard](#accessing-the-flink-dashboard)
    * [Shutting Down the Application](#shutting-down-the-application)
* [Running the Examples](#running-the-examples)
* [Running the Experiments](#running-the-experiments)
* [Troubleshooting Common Issues](#troubleshooting-common-issues)
    * [`java.nio.file.FileSystemException`](#javaniofilefilesystemexception)
    * [Connection Issues on WSL](#connection-issues-on-wsl)
<!-- TOC -->

# Requirements
Before installing the Fruit Stream Processing app, ensure your system meets the following requirements.

1. **Flink:**
   - A UNIX-like systems, including Linux, Mac OS X, and [Cygwin](https://cygwin.com/install.html) or WSL (for Windows).
   - [Java 11](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html) - Verify your Java installation
     by executing `java -version` in your command prompt.
   - [Apache Flink](https://flink.apache.org/).
2. **MySQL:**
   - [MySQL](https://www.mysql.com/) and its server installed and running on your system.

# Installation

1. **Clone the Repository:** Clone the Fruit Stream Processing application repository to your system.
    ```shell
       git clone https://github.com/eleadufresne/fruit-stream-processor.git
    ```
2. **Flink Setup:** If you haven't installed Apache Flink, follow this [tutorial](https://nightlies.apache.org/flink/flink-docs-stable/docs/try-flink/local_installation/#first-steps)
   to get started. You might need to install the latest [Hadoop](https://hadoop.apache.org/releases.html) binaries.
3. **MySQL Setup:**  Start MySQL server on your system. If it's not installed, run the following commands
   before proceeding.
    ```shell
    # install MySQL
    sudo apt-get update
    sudo apt install mysql-server
    # ensure the MySQL server has been started
    sudo service mysql status
    # [optional] start the server if it is not running correctly
    sudo service mysql restart
    ```
With these installations and setups complete, you're ready to configure and run the Fruit Stream Processing application.

# Setup Configuration
Please keep in mind the following steps when setting up WSL. If you're using a different environment, you may need 
to make some additional configurations.  

1. **Environment Variable:** As a first step, let's create an environment variable for the flink installation directory.
  The command should look something like that.
    ```shell
    export FLINK_HOME=/mnt/c/projects/apache/flink-1.18.0
    ```
   Optionally, you may set up an environment variable for this repository. Otherwise replace `$FRUIT_DIR` with the actual 
   path in the following steps.
    ```shell
    export FRUIT_DIR=/mnt/c/projects/fruit-stream-processor
    ```
2. **Permissions:** Ensure that you have enough permissions to use the repository.
    ```shell
    chmod -R 777 $FLINK_HOME
    ```
3. **Configuration File:** Navigate to the `conf` folder of your Flink directory and open the ``flink-conf.yaml`` file.
   1. **Setup the Logs:** Add the following line to the end of the file:
      ```yaml
      taskmanager.resource-id: stream-processing-logs
      ```
      Flink will create a temporary folder with the given name to maintain its state logs as it sets up a cluster.
      These logs can also be accessed in the web UI under the task manager tab.

   2. **Setup the Connection:** Update the following values in `flink-conf.yaml`.  
      ```yaml
      rest.port: 8081
      rest.address: localhost
      rest.bind-adress: 0.0.0.0
      ```
      This should allow you to access Flink via your Windows 'localhost' rather than the IP of your WSL instance.

4. **Setup the Database:** To run the app, you must first create database with appropriate schema to store the output.
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
    # log in as the newly created user, and type in the password
    mysql -u fruit_enthusiast -p
    # create a new database
    CREATE DATABASE fruits;
    # create a table inside the database
    USE fruits
    CREATE TABLE data
    (
        feature         VARCHAR(150) NOT NULL,  # ripe, rotten, etc.
        count           INT unsigned NOT NULL,  # number of fruit with this feature
        PRIMARY KEY     (feature)               # make the feature the primary key
    );
    ```
   
5. **[Optional] Faster WSL Network:** Modify your ``.wslconfig`` file so it contains the following lines.
    ```editorconfig
    [wsl2]
    nestedVirtualization=true
    memory=32GB
    vmSwitch = LAN
    experimental.networkingMode=mirrored
    localhostforwarding=true
    [boot]
    systemd=true
    ```

6. **[Optional] Performance Monitoring Dashboards** Although Flink provides a dashboard with some useful metrics, it might be worth 
installing Prometheus and Grafana if you are looking for better data visualization. Keep in mind that if you decide 
**not** to install them, you will need to comment out the relevant lines in the experiment script.
    ```shell
   # install prometheus
   sudo apt-get install prometheus
    # install Grafana
    sudo apt-get install grafana
    # start the Grafana server
    sudo service grafana-server start
    ```
    Open a browser on your Windows machine and navigate to [localhost:3000](http://localhost:3000/). Log in using the default 
    credentials (admin/admin). Create a new dashboard for this experiment by uploading the `json` template we provide under
    the config folder. We also provide a Flink config that enables Prometheus, and opens up ports for it. In order to use 
    this reporter you must copy ``/opt/flink-metrics-prometheus-1.18.0.jar`` into the ``/lib`` folder of your Flink distribution

# Running the Application

### Launching the Application
By default, the job monitors files from ``orchard-watch`` (provided under ``util``) in the working directory. Before 
proceeding, either place ``orchard-watch`` to your working directory or provide paths as argument. Now, from the root  
of your UNIX-like system, execute the following commands. 

```shell
# start a cluster
$FLINK_HOME/bin/start-cluster.sh
# generate a JAR file for the Flink job
cd $FRUIT_DIR && mvn clean package && cd ~
# start monitoring the directory
$FLINK_HOME/bin/flink run $FRUIT_DIR/tar*/fruit*.jar <optional-arguments>
```
Run ``$FLINK_HOME/bin/flink run $FRUIT_DIR/tar*/fruit*.jar --help`` to get the list of arguments.

### Accessing the Flink Dashboard
To access the Flink dashboard:

1. **Standard Access**:
   - Navigate to [localhost:8081](http://localhost:8081) in your web browser. This is the default address for the Flink dashboard.

2. **Accessing from WSL**:
   - If you are using WSL, and `localhost` does not work, you may need to use the IP address of your WSL instance.
   - Find the IP address by running `hostname -I` in your WSL terminal.
   - Then, access the dashboard via **http://<your-wsl-ip>:8081** in your browser.
   
If you encounter any issues accessing the dashboard from WSL, please refer to the [Connection Issues on WSL](#connection-issues-on-wsl) 
section for further troubleshooting.

### Shutting Down the Application

When you are done, stop the cluster with`$FLINK_HOME/bin/stop-cluster.sh` and shut down the MySQL server with 
``mysqladmin --user root shutdown``.

# Running the Examples
The program features two modes of execution, one for processing images and another for text files. To run both, simply 
execute the ``run_examples.sh`` file. The output can be found under the ``./orchard-watch/output`` directory.

# Running the Experiments
Make sure that you have correctly [configured the environment](#setup-configuration) before proceeding further. 
We have provided a script that enables you to execute all experiments and evaluate performance on a large input, 
throughput, latency, and bandwidth.

To execute the experiments, you need to launch the "run_experiments.sh" script with the option 0. This will initiate all 
the experiments without having to build the JAR file. You can also perform individual experiments that are stored in 
the ``script`` directory.

Once you have launched the script, you can open the Flink dashboard on your web browser at[localhost:8081](http://localhost:8081). 
To access Prometheus, go to [localhost:9090](http://localhost:9090), and for Grafana, visit [localhost:3000](http://localhost:3000).

In terms of monitoring, we are interested in the following metrics:
1. ``numRecordsOutPerSecond`` - Number of records this task/operator sends per second. (throughput)
2. ``latency`` - Latency from the source operator to this operator.
3. ``restartingTime `` - The time it took to restart the job, or how long the  current restart has been in progress.

# Troubleshooting Common Issues

### `java.nio.file.FileSystemException`

If you come across a `java.nio.file.FileSystemException`, it may indicate that the task-executor failed to stop properly. 
To tackle this issue, simply run the `jsp` command in your UNIX-like terminal. This will display the running processes. 
Keep executing `$FLINK_HOME/bin/stop-cluster.sh` until all standalone-sessions and task-manager instances are terminated.

**Alternatively**, you may use the Windows Resource Monitor to find out which processes are using the file and 
terminate them manually.

1. Open the Task Manager (Ctrl + Shift + Esc).
2. Go to the "Performance" tab.
3. Click on "Open Resource Monitor" at the bottom.
4. In the Resource Monitor, navigate to the "CPU" tab.
5. Expand the "Associated Handles" section.
6. Type in the name of the file you're looking for in the search box.
7. Terminate any process that is using the file.

### Connection Issues on WSL
If you're using WSL, you might need to add a rule to open up ports for Flink since WSL runs on its own IP address.
In the **Windows PowerShell**, execute the following to create a new rule.

```shell
# open ports 1000-60000 (feels free to make it stricter)
New-NetFireWallRule -Profile Private -DisplayName 'Open ports for Flink on WSL' 
    -Direction Inbound -LocalPort 1000-65000 -Action Allow -Protocol TCP
# restart wsl to apply changes
wsl --shutdown
wsl
```

In your **WSL** terminal (e.g. Ubuntu), you may open all -or specific- ports using the following commands.

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
