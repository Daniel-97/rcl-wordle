#!/bin/bash

# Compile server
javac -d target -sourcepath src -cp src/resources/gson-2.10.1.jar src/server/ServerMain.java
# Compile client
javac -d target -sourcepath src -cp src/resources/gson-2.10.1.jar src/client/ClientMain.java

# Run server
#WORDLE_CONFIG=server.config java -cp src/resources/gson-2.10.1.jar:target server.ServerMain server.config

# Run client
#WORDLE_CONFIG=client.config java -cp src/resources/gson-2.10.1.jar:target client.ClientMain client.config

jar -cvfe Server.jar target/server/ServerMain target # funziona
