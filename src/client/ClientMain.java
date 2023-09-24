package client;

import client.enums.GuestCommand;
import client.services.CLIHelper;
import server.exceptions.WordleException;
import server.interfaces.ServerRMI;
import utils.ConfigReader;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Properties;

public class ClientMain {

	private static final String STUB_NAME = "WORDLE-SERVER";
	private final int tcpPort;
	private final int rmiPort;

	public static void main(String[] argv) {

		ClientMain client = new ClientMain(null); // Todo prendere config path da argomenti

		// Inizializza RMI
		ServerRMI serverRMI = null;
		Remote RemoteObject = null;

		try {
			// TODO capire come specificare indirizzo ip remoto del server
			Registry registry = LocateRegistry.getRegistry(client.rmiPort);
			RemoteObject = registry.lookup(STUB_NAME);
			serverRMI = (ServerRMI) RemoteObject;

		} catch (RemoteException e) {
			System.out.println("Client remote exception: " + e.getMessage());
			System.exit(-1);
		} catch (NotBoundException e) {
			System.out.println("Client not bound exception" + e.getMessage());
			System.exit(-1);
		}

		boolean loggedIn = false;
		while (!loggedIn) {

			CLIHelper.entryMenu();
			// Utente non ancora loggato
			String[] input = CLIHelper.parseInput();
			String cmd = input[0];
			String[] args = Arrays.copyOfRange(input, 1, input.length);

			GuestCommand gCommand = GuestCommand.fromCommand(cmd);
			if( gCommand == null ) {
				System.out.println("Invalid command!");
				continue;
			}

			// Eseguo un comando
			System.out.println("Valid command!");
			switch (gCommand) {
				case HELP:
					CLIHelper.entryMenu();
					break;

				case QUIT:
					System.exit(0);
					break;

				case LOGIN:
					System.out.println("Not implmented");
					break;

				case REGISTER:
					try {
						//TODO controllare input utente
						System.out.println(args[0]);
						System.out.println(args[1]);
						serverRMI.register(args[0], args[1]);
					}catch (RemoteException | WordleException e) {
						throw new RuntimeException(e);
					}

					break;
			}
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
