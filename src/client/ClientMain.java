package client;

import client.enums.GuestCommand;
import client.enums.UserCommand;
import client.services.CLIHelper;
import common.dto.TcpMessageDTO;
import server.exceptions.WordleException;
import server.interfaces.ServerRMI;
import common.utils.ConfigReader;
import common.utils.SocketUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

public class ClientMain {

	private static final String STUB_NAME = "WORDLE-SERVER";
	private final int tcpPort;
	private final int rmiPort;
	private final String serverIP;
	private ServerRMI serverRMI;
	private SocketChannel socket;


	public static void main(String[] argv) {

		ClientMain client = new ClientMain(null); // Todo prendere config path da argomenti

		System.out.println("Tenativo di connessione con il server "+client.serverIP+":"+client.tcpPort);
		try {
			client.socket = SocketChannel.open();
			client.socket.connect(new InetSocketAddress(client.serverIP, client.tcpPort));
		} catch (IOException e) {
			System.out.println("Errore durante connessione tcp al server " + client.serverIP + ":"+client.tcpPort);
			System.exit(-1);
		}

		// Thread in ascolto di SIGINT e SIGTERM
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown Wordle client...");
				//socketChannel.close();
			}
		});

		System.out.println("Connesso con il server "+client.serverIP+":"+client.tcpPort);
		Scanner scanner = new Scanner(System.in);

		boolean loggedIn = false;
		String username = null;

		while (true) {

			//Fino a che utente non loggato, mostro il menu iniziale
			while (!loggedIn) {

				System.out.println("Premere un tasto per continuare...");
				scanner.nextLine();

				CLIHelper.entryMenu();
				// Utente non ancora loggato
				String[] input = CLIHelper.parseInput();
				String cmd = input[0];
				String[] args = Arrays.copyOfRange(input, 1, input.length);

				GuestCommand gCommand = GuestCommand.fromCommand(cmd);
				if (gCommand == null) {
					System.out.println("Invalid command!");
					continue;
				}

				// Eseguo un comando
				switch (gCommand) {
					case HELP:
						CLIHelper.entryMenu();
						break;

					case QUIT:
						try {
							client.socket.close();
						} catch (IOException e) {
							System.out.println("Errore chiusura socket con server");
						} finally {
							System.exit(0);
						}
						break;

					case LOGIN:
						if (args.length < 2) {
							System.out.println("Comando non completo");
						} else {
							loggedIn = client.login(args[0], args[1]);
							if(loggedIn) {
								username = args[0];
							}
						}
						break;

					case REGISTER:
						if (args.length < 2) {
							System.out.println("Comando non completo");
						} else {
							client.register(args[0], args[1]);
						}
						break;
				}
			}

			CLIHelper.mainMenu();
			String[] input = CLIHelper.parseInput();
			String cmd = input[0];
			String[] args = Arrays.copyOfRange(input, 1, input.length);

			UserCommand uCommand = UserCommand.fromCommand(cmd);
			if (uCommand == null) {
				System.out.println("Invalid command!");
				continue;
			}

			switch (uCommand) {
				case HELP:
					CLIHelper.mainMenu();
					break;

				case LOGOUT:
					boolean success = client.logout(username);
					if (success) {
						loggedIn = false;
						username = null;
					}
					break;

				default:
					System.out.println("Comando sconosciuto");

			}

		}



	}

	public boolean login(String username, String password) {

		TcpMessageDTO requestDTO = new TcpMessageDTO("login", new String[]{username, password});

		try {
			SocketUtils.sendTcpMessage(this.socket, requestDTO);

			TcpMessageDTO response = SocketUtils.readTcpMessage(this.socket);
			if (response.success) {
				System.out.println("Login completato con successo");
				return true;
			} else {
				System.out.println("Errore durante il login!");
				return false;
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login!");
			return false;
		}
	}

	public boolean logout(String username) {

		TcpMessageDTO request = new TcpMessageDTO("logout", new String[]{username});

		try {
			SocketUtils.sendTcpMessage(this.socket, request);

			TcpMessageDTO response = SocketUtils.readTcpMessage(this.socket);
			if (response.success) {
				System.out.println("Logout completato con successo");
				return true;
			} else {
				System.out.println("Errore durante il logut!");
				return false;
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login");
			return false;
		}
	}

	public void register(String username, String password) {

		try {
			this.serverRMI.register(username, password);
		} catch (RemoteException e) {
			// TODO gestire il caso in cui il server si disconnette
			throw new RuntimeException(e);
		} catch (WordleException e) {
			System.out.println("Errore! " + e.getMessage());
		}
	}

	public ClientMain(String configPath) {

		System.out.println("Avvio Wordle game client...");

		if (configPath == null || configPath.isEmpty()) {
			System.out.println("Nessun file di configurazione trovato, uso file di configurazione di default");
			configPath = "./src/client/app.config";
		}

		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig(configPath);
		this.tcpPort = Integer.parseInt(properties.getProperty("app.tcp.port"));
		this.rmiPort = Integer.parseInt(properties.getProperty("app.rmi.port"));
		this.serverIP = properties.getProperty("app.tcp.ip");

		// Inizializza RMI
		Remote RemoteObject = null;
		try {
			// TODO capire come specificare indirizzo ip remoto del server
			Registry registry = LocateRegistry.getRegistry(this.rmiPort);
			RemoteObject = registry.lookup(STUB_NAME);
			this.serverRMI = (ServerRMI) RemoteObject;

		} catch (RemoteException e) {
			System.out.println("Client remote exception: " + e.getMessage());
			System.exit(-1);
		} catch (NotBoundException e) {
			System.out.println("Client not bound exception" + e.getMessage());
			System.exit(-1);
		}

	}
}
