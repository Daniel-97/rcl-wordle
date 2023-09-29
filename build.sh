#!/bin/bash

# Compile server
javac -target 1.8 -d target -sourcepath src -cp src/resources/gson-2.10.1.jar src/server/ServerMain.java
# Compile client
javac -d target -sourcepath src -cp src/resources/gson-2.10.1.jar src/client/ClientMain.java

# Run server
#java -cp src/resources/gson-2.10.1.jar:target server.ServerMain server.config

# Run client
#java -cp src/resources/gson-2.10.1.jar:target client.ClientMain client.config

