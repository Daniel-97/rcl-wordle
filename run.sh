#!/bin/bash
# Questo script consente di avviare il server e il client utilizzando dei file di configurazione predefiniti e
# il server in modalita' debug.

if [ "$1" == "server" ]; then
  WORDLE_DEBUG=true WORDLE_CONFIG=server.config java -jar target/Server-jar-with-dependencies.jar
elif [ "$1" == "client" ]; then
  WORDLE_CONFIG=client.config java -jar target/Client-jar-with-dependencies.jar
else
  echo "Devi specificare se vuoi avviare il server o il client"
fi