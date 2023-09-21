package client;

import utils.ConfigReader;

import java.util.Properties;

public class ClientMain {

	private final int tcpPort;
	private final int rmiPort;

	public static void main(String[] argv) {

		ClientMain client = new ClientMain(null); // Todo prendere config path da argomenti
	}

	public ClientMain(String configPath){

		System.out.println("Avvio Wordle game client...");

		if (configPath == null || configPath.isEmpty()) {
			System.out.println("Nessun file di configurazione trovato, uso file di configurazione di default");
			configPath = "./src/server/app.config";
		}

		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig(configPath);
		this.tcpPort = Integer.parseInt(properties.getProperty("app.tcp.port"));
		this.rmiPort = Integer.parseInt(properties.getProperty("app.rmi.port"));

	}
}
