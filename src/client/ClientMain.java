package client;

import client.enums.ClientMode;
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
	private String username = null;
	private ClientMode mode = ClientMode.GUEST_MODE;


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

		client.run();
	}

	public void run() {

		while (true) {

			switch (this.mode) {

				case GUEST_MODE:
					this.guestMode();
					break;

				case USER_MODE:
					this.userMode();
					break;

				case GAME_MODE:
					this.gameMode();
					break;

			}

		}
	}

	private void guestMode() {

		CLIHelper.entryMenu();
		String[] input = CLIHelper.parseInput();

		GuestCommand cmd = GuestCommand.fromCommand(input[0]);
		if (cmd == null) {
			System.out.println("Comando non valido!");
			return;
		}

		// Eseguo un comando
		switch (cmd) {
			case HELP:
				CLIHelper.entryMenu();
				break;

			case QUIT:
				try {
					this.socket.close();
				} catch (IOException e) {
					System.out.println("Errore chiusura socket con server");
				} finally {
					System.exit(0);
				}
				break;

			case LOGIN:
				if (input.length < 3) {
					System.out.println("Comando non valido!");
				} else {
					this.login(input[1], input[2]);
				}
				break;

			case REGISTER:
				if (input.length < 3) {
					System.out.println("Comando non completo");
				} else {
					this.register(input[1], input[2]);
				}
				break;
		}

		//CLIHelper.cls();
	}

	private void userMode() {

		CLIHelper.mainMenu();
		String[] input = CLIHelper.parseInput();

		UserCommand cmd = UserCommand.fromCommand(input[0]);
		if (cmd == null) {
			System.out.println("Invalid command!");
			return;
		}

		switch (cmd) {
			case HELP: {
				CLIHelper.mainMenu();
				break;
			}

			case LOGOUT: {
				this.logout(this.username);
				break;
			}

			case PLAY: {
				this.playWORDLE();
				break;
			}
			default:
				System.out.println("Comando sconosciuto");

		}
	}

	private void gameMode() {
		System.out.println("GAME MODE!");
		String[] input = CLIHelper.parseInput();
	}

	private void login(String username, String password) {

		TcpMessageDTO requestDTO = new TcpMessageDTO("login", new String[]{username, password});

		try {
			SocketUtils.sendTcpMessage(this.socket, requestDTO);

			TcpMessageDTO response = SocketUtils.readTcpMessage(this.socket);
			if (response.success) {
				System.out.println("Login completato con successo");
				this.mode = ClientMode.USER_MODE;
				this.username = username;
			} else {
				System.out.println("Errore durante il login!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login!");
		}
	}

	private void logout(String username) {

		TcpMessageDTO request = new TcpMessageDTO("logout", new String[]{username});

		try {
			SocketUtils.sendTcpMessage(this.socket, request);

			TcpMessageDTO response = SocketUtils.readTcpMessage(this.socket);
			if (response.success) {
				System.out.println("Logout completato con successo");
				this.username = null;
				this.mode = ClientMode.GUEST_MODE;
			} else {
				System.out.println("Errore durante il logut!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login");
		}
	}

	private void playWORDLE() {
		TcpMessageDTO request = new TcpMessageDTO("playWORDLE", new String[]{username});
		try {
			SocketUtils.sendTcpMessage(this.socket, request);

			TcpMessageDTO response = SocketUtils.readTcpMessage(this.socket);
			if (response.success) {
				System.out.println("Ok, puoi giocare a Wordle!");
				this.mode = ClientMode.GAME_MODE;
			} else {
				System.out.println("Errore, non puoi giocare con attuale parola");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante richiesta di playWORDLE");
		}
	}

	private void register(String username, String password) {

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
