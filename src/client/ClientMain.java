package client;

import client.enums.ClientMode;
import client.enums.GuestCommand;
import client.enums.UserCommand;
import client.services.CLIHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.dto.TcpClientRequestDTO;
import common.dto.TcpServerResponseDTO;
import common.enums.ResponseCodeEnum;
import server.exceptions.WordleException;
import server.interfaces.ServerRMI;
import common.utils.ConfigReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Properties;

public class ClientMain {

	private final static int BUFFER_SIZE = 1024;
	private final static Gson gson = new GsonBuilder().create();
	private static int TCP_PORT;
	private static int RMI_PORT;
	private static String STUB_NAME = "WORDLE-SERVER";
	private static String SERVER_IP;
	private static ServerRMI serverRMI;
	private static SocketChannel socketChannel;
	private static String username = null;
	private ClientMode mode = ClientMode.GUEST_MODE;
	private boolean canPlayWord = false;

	private static final String TITLE =
			" __        _____  ____  ____  _     _____    ____ _     ___ _____ _   _ _____ \n" +
			" \\ \\      / / _ \\|  _ \\|  _ \\| |   | ____|  / ___| |   |_ _| ____| \\ | |_   _|\n" +
			"  \\ \\ /\\ / / | | | |_) | | | | |   |  _|   | |   | |    | ||  _| |  \\| | | |  \n" +
			"   \\ V  V /| |_| |  _ <| |_| | |___| |___  | |___| |___ | || |___| |\\  | | |  \n" +
			"    \\_/\\_/  \\___/|_| \\_\\____/|_____|_____|  \\____|_____|___|_____|_| \\_| |_|  ";

	public static void main(String[] argv) {

		System.out.println(TITLE);
		ClientMain client = new ClientMain();

		// Thread in ascolto di SIGINT e SIGTERM
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown Wordle client...");
				try {
					socketChannel.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});

		client.run();
	}

	public void run() {

		CLIHelper.pause();
		while (true) {

			switch (mode) {

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
			System.out.println("Comando non trovato!");
			CLIHelper.pause();
			return;
		}

		// Eseguo un comando
		switch (cmd) {
			case HELP:
				CLIHelper.pause();
				break;

			case QUIT:
				try {
					socketChannel.close();
				} catch (IOException e) {
					System.out.println("Errore chiusura socket con server");
				}
				System.exit(0);
				break;

			case LOGIN:
				if (input.length < 3) {
					System.out.println("Comando non valido!");
				} else {
					this.login(input[1], input[2]);
				}
				CLIHelper.pause();
				break;

			case REGISTER:
				if (input.length < 3) {
					System.out.println("Comando non completo");
				} else {
					this.register(input[1], input[2]);
				}
				CLIHelper.pause();
				break;
		}

	}

	private void userMode() {

		CLIHelper.mainMenu();
		String[] input = CLIHelper.parseInput();

		UserCommand cmd = UserCommand.fromCommand(input[0]);
		if (cmd == null) {
			System.out.println("Comando non trovato!");
			CLIHelper.pause();
			return;
		}

		switch (cmd) {
			case HELP: {
				CLIHelper.pause();
				CLIHelper.mainMenu();
				break;
			}

			case LOGOUT: {
				this.logout(username);
				break;
			}

			case PLAY: {
				// Prima di iniziare il gioco devo chiedere al server se l utente puo iniziare o meno
				this.playWORDLE();
				if(canPlayWord) {
					this.mode = ClientMode.GAME_MODE;
				}
				CLIHelper.pause();
				break;
			}

			/*
			case SEND_WORD:
				if (input.length < 2) {
					System.out.println("Comando non valido!");
				} else {
					this.sendWord(input[1]);
				}
				CLIHelper.cls();
				break;
			*/
			case STAT:
				this.sendMeStatistics();
				CLIHelper.pause();
				break;

			case SHARE:
				System.out.println("Condivido le statistiche con gli altri utenti");
				CLIHelper.pause();
				break;
		}
	}

	private void gameMode() {

		System.out.println("GAME MODE! Digita in qualsiasi momento :exit per uscire dalla modalita' gioco!");
		while (true) {
			System.out.println("Inserisci una parola:");
			String[] input = CLIHelper.parseInput();

			if(input[0].equals(":exit")) {
				this.mode = ClientMode.USER_MODE;
				break;
			}

			CLIHelper.cls();
			this.sendWord(input[0]);
		}
	}

	private void sendWord(String word) {

		if (!canPlayWord) {
			System.out.println("Errore, richiedi prima al server di poter giocare la parola attuale");
			return;
		}

		TcpServerResponseDTO response = null;
		TcpClientRequestDTO request = new TcpClientRequestDTO("sendWord", new String[]{username, word});
		try {
			sendTcpMessage(request);
			response = readTcpMessage();
		} catch (IOException e) {
			System.out.println("Errore durante invio guessed word");
			return;
		}

		if(response != null) {
			if (response.code == ResponseCodeEnum.GAME_WON) {
				System.out.println("Hai indovinato la parola!");
				canPlayWord = false;
			} else if(response.code == ResponseCodeEnum.INVALID_WORD_LENGHT) {
				System.out.println("Parola troppo lunga o troppo corta, tentativo non valido");
			} else if(response.code == ResponseCodeEnum.WORD_NOT_IN_DICTIONARY) {
				System.out.println("Parola non presente nel dizionario, tentativo non valido");
			} else if(response.code == ResponseCodeEnum.GAME_LOST) {
				System.out.println("Tentativi esauriti!");
				canPlayWord = false;
			} else {
				CLIHelper.printServerWord(response.userGuess);
			}
		}

	}

	private void login(String username, String password) {

		TcpClientRequestDTO requestDTO = new TcpClientRequestDTO("login", new String[]{username, password});

		try {
			sendTcpMessage(requestDTO);

			TcpServerResponseDTO response = readTcpMessage();
			if (response.success) {
				System.out.println("Login completato con successo");
				mode = ClientMode.USER_MODE;
				ClientMain.username = username;
			} else {
				System.out.println("Nome utente o password errati!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login!");
		}
	}

	private void logout(String username) {

		TcpClientRequestDTO request = new TcpClientRequestDTO("logout", new String[]{username});

		try {
			sendTcpMessage(request);

			TcpServerResponseDTO response = readTcpMessage();
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

	/**
	 * Richiedo al server se l'utente puo' iniziare a giocare
	 */
	private void playWORDLE() {
		TcpClientRequestDTO request = new TcpClientRequestDTO("playWORDLE", new String[]{username});
		try {
			sendTcpMessage(request);

			TcpServerResponseDTO response = readTcpMessage();
			if (response.success) {
				System.out.println("Ok, puoi giocare a Wordle!");
				canPlayWord = true;
			} else {
				System.out.println("Errore, non puoi giocare con parola attuale");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante richiesta di playWORDLE");
		}
	}

	private void register(String username, String password) {

		try {
			serverRMI.register(username, password);
			System.out.println("Complimenti! Registrazione completata con successo!");
		} catch (RemoteException e) {
			// TODO gestire il caso in cui il server si disconnette
			throw new RuntimeException(e);
		} catch (WordleException e) {
			System.out.println("Errore durante la registrazione! " + e.getMessage());
		}
	}

	private void sendMeStatistics() {
		TcpClientRequestDTO request = new TcpClientRequestDTO("stat", new String[]{username});
		try {
			sendTcpMessage(request);

			TcpServerResponseDTO response = readTcpMessage();
			if(response != null && response.stat != null) {
				CLIHelper.printUserStats(response.stat);
			} else {
				System.out.println("Statistiche mancanti!");
			}
		} catch (IOException e) {
			System.out.println("Errore richiesta statistiche");
		}
	}

	public ClientMain() {

		System.out.println("Avvio Wordle game client...");

		// Leggo file di configurazione
		Properties properties = ConfigReader.readConfig();
		try {
			RMI_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.rmi.port"));
			TCP_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.tcp.port"));
			SERVER_IP = ConfigReader.readProperty(properties, "app.tcp.ip");
		} catch (NoSuchFieldException e) {
			System.out.println("Parametro di configurazione non trovato! " + e.getMessage());
			System.exit(-1);
		} catch (NumberFormatException e) {
			System.out.println("Parametro di configurazione malformato! " + e.getMessage());
			System.exit(-1);
		}

		// Inizializza RMI
		try {
			// TODO capire come specificare indirizzo ip remoto del server
			Registry registry = LocateRegistry.getRegistry(RMI_PORT);
			Remote RemoteObject = registry.lookup(STUB_NAME);
			serverRMI = (ServerRMI) RemoteObject;
			System.out.println("Lookup registro RMI server riuscito! Stub: " + STUB_NAME);

		} catch (RemoteException e) {
			System.out.println("Errore connessione RMI, controlla che il server sia online: " + e.getMessage());
			System.exit(-1);

		} catch (NotBoundException e) {
			System.out.println("Client not bound exception" + e.getMessage());
			System.exit(-1);
		}

		// Inizializza connessione TCP
		try {
			socketChannel = SocketChannel.open();
			socketChannel.connect(new InetSocketAddress(SERVER_IP, TCP_PORT));
			System.out.println("Connessione TCP con il server riuscita! "+SERVER_IP+":"+TCP_PORT);
		} catch (IOException e) {
			System.out.println("Errore durante connessione TCP al server: "+ e.getMessage());
			System.exit(-1);
		}

	}

	public static void sendTcpMessage(TcpClientRequestDTO request) throws IOException {
		String json = gson.toJson(request);
		ByteBuffer command = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		socketChannel.write(command);
	}

	public static TcpServerResponseDTO readTcpMessage() throws IOException {

		ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
		StringBuilder json = new StringBuilder();
		Socket socket = socketChannel.socket();
		BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		String line;
		// TODO sistemare non funziona
		while((line = reader.readLine()) != null) {
			json.append(line);
			System.out.println(line);
		}

		System.out.println(json);
		return gson.fromJson(json.toString(), TcpServerResponseDTO.class);
	}
}
