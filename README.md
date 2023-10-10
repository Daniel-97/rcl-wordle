# rcl-wordle
Progetto laboratorio reti di calcolatori Unipi

# BUILD
mvn clean install

# RUN
WORDLE_CONFIG=server.config java -jar target/Server-jar-with-dependencies.jar
WORDLE_CONFIG=client.config java -jar target/Client-jar-with-dependencies.jar

