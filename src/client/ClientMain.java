package client;

import server.exceptions.WordleException;
import server.interfaces.ServerRMI;
import utils.ConfigReader;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Properties;

public class ClientMain {

	private static final String STUB_NAME = "WORDLE-SERVER";
	private final int tcpPort;
	private final int rmiPort;

	public static void main(String[] argv) {

		ClientMain client = new ClientMain(null); // Todo prendere config path da argomenti

		// Inizializza RMI
		ServerRMI serverRMI;
		Remote RemoteObject;

		try {
			// TODO capire come specificare indirizzo ip remoto del server
			Registry registry = LocateRegistry.getRegistry(client.rmiPort);
			RemoteObject = registry.lookup(STUB_NAME);
			serverRMI = (ServerRMI) RemoteObject;

			// prova connessione
			serverRMI.register("test", "test");

		} catch (RemoteException e) {
			System.out.println("Client remote exception: " + e.getMessage());
		} catch (NotBoundException e) {
			System.out.println("Client not bound exception" + e.getMessage());
		} catch (WordleException e) {
			System.out.println("Wordle exception: " + e.getMessage());
		}


	}

	public ClientMain(String configPath) {

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
