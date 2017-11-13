# Distributed Group Membership
This project implements distributed group membership system by a crash/fail-stop model, that allows machines to join/leave the group and detect failure when machine crash.

## Package Dependencies
- Java 8

## Instructions
### Step 1 - SetUp
1. Type ```git clone git@gitlab.engr.illinois.edu:fa17-cs425-g04/MP2.git``` to download the files to local machine.
2. Type ```cd $ROOT/src``` where ```$ROOT``` is the project root directory.
3. Type ```javac Daemon.java``` to compile files.

### Step 2 - Edit Configuration File
There are 4 lines in the file: ```hostNames```, ```joinPortNumber```, ```packetPortNumber```, and ```logPath```. ```hostNames``` defines the introducer machines in the distributed group membership system, ```joinPortNumber``` defines which port the introducer listens to new member join request, ```packetPortNumber``` defines which port the daemon process listens to heartbeat and gossip, and ```logPath``` defines the path to the system log in each machine.

1. Type ```cd $ROOT/config/```
2. Type ```vim config.properties``` to edit the configuration file.

### Step 3 - Run Introducer Daemon Process
1. Type ```cd $ROOT/src/```
2. Type ```java Daemon ../config/config.properties -i``` to run the introducer daemon process. The introducer daemon process has extra functionality in addition to regular daemon process, which is allowing new machine to join the group.
3. Upon the prompt shows, enter "ID" to show the machine ID and enter "JOIN" to run the introducer daemon process and join the group.

### Step 4 - Run Daemon Process
1. Type ```cd $ROOT/src/```
2. Type ```java Daemon ../config/config.properties``` to run the daemon process.
3. Upon the prompt shows, enter "ID" to show the machine ID and enter "JOIN" to run the daemon process and join the group.
4. If there is no introducer alive, you would not able to join the group.

### Step 5 - Show Membership List
1. Once the daemon process joins the group, type "MEMBER" in the prompt to show the current membership list.

### Step 6 - Leave Group
1. Type "LEAVE" in the prompt to voluntarily leaves the group.
