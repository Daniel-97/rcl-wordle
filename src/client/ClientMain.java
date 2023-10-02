package client;

import client.enums.ClientMode;
import client.enums.UserCommand;
import client.services.CLIHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.dto.LetterDTO;
import common.dto.TcpClientRequestDTO;
import common.dto.TcpServerResponseDTO;
import common.enums.ResponseCodeEnum;
import common.interfaces.NotifyEventInterface;
import server.exceptions.WordleException;
import common.interfaces.ServerRmiInterface;
import common.utils.ConfigReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

public class ClientMain extends RemoteObject implements NotifyEventInterface {

	private final static int BUFFER_SIZE = 1024;
	private final static Gson gson = new GsonBuilder().create();
	private static int TCP_PORT;
	private static int RMI_PORT;
	private static String STUB_NAME = "WORDLE-SERVER";
	private static String SERVER_IP;
	private static ServerRmiInterface serverRMI;
	private static NotifyEventInterface stub;
	private static SocketChannel socketChannel;
	private static String username = null;
	private ClientMode mode = ClientMode.GUEST_MODE;
	private boolean canPlayWord = false;
	private int remainingAttempts;
	private LetterDTO[][] guesses;

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

	public ClientMain() {

		super();
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

		// RMI e callback
		try {
			Registry registry = LocateRegistry.getRegistry(RMI_PORT);
			Remote RemoteObject = registry.lookup(STUB_NAME);
			serverRMI = (ServerRmiInterface) RemoteObject;
			System.out.println("Lookup registro RMI server riuscito! Stub: " + STUB_NAME);

			// Callback (esporta oggetto)
			stub = (NotifyEventInterface) UnicastRemoteObject.exportObject(this, 0);

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
		String[] input = CLIHelper.waitForInput();

		UserCommand cmd = UserCommand.fromCommand(input[0]);
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
		String[] input = CLIHelper.waitForInput();

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

			case STAT:
				this.sendMeStatistics();
				CLIHelper.pause();
				break;

			case SHARE:
				System.out.println("Condivido le statistiche con gli altri utenti");
				CLIHelper.pause();
				break;

			case QUIT:
				System.exit(0);
		}
	}

	private void gameMode() {

		System.out.println("GAME MODE! Digita in qualsiasi momento :exit per uscire dalla modalita' gioco!");
		while (mode == ClientMode.GAME_MODE) {
			if(guesses.length > 0) {
				System.out.println("I tuoi tentativi:");
				CLIHelper.printServerWord(guesses);
			}
			System.out.println("Hai ancora " + remainingAttempts + " tentativi rimasti. Inserisci una parola:");
			String[] input = CLIHelper.waitForInput();

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
				System.out.println("Complimenti, hai indovinato la parola!");
				canPlayWord = false;
				mode = ClientMode.USER_MODE;
				CLIHelper.pause();
			} else if(response.code == ResponseCodeEnum.INVALID_WORD_LENGHT) {
				System.out.println("Parola troppo lunga o troppo corta, tentativo non valido");
				CLIHelper.pause();
			} else if(response.code == ResponseCodeEnum.WORD_NOT_IN_DICTIONARY) {
				System.out.println("Parola non presente nel dizionario, tentativo non valido");
				CLIHelper.pause();
			} else if(response.code == ResponseCodeEnum.GAME_LOST) {
				System.out.println("Tentativi esauriti, hai perso!");
				CLIHelper.pause();
				canPlayWord = false;
			} else if(response.code == ResponseCodeEnum.GAME_ALREADY_PLAYED) {
				System.out.println("Hai gia' giocato a questa parola!");
				CLIHelper.pause();
			}
			else {
				System.out.println("Parola non indovinata!");
				guesses = response.userGuess;
			}
			remainingAttempts = response.remainingAttempts;
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
				// Registra l'utente per le callback dal server
				serverRMI.subscribeClientToEvent(username, stub);
			} else {
				System.out.println("Nome utente o password errati!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login! " + e.getMessage());
			System.exit(-1);
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
				remainingAttempts = response.remainingAttempts;
				guesses = response.userGuess;
			} else {
				System.out.println("Errore, non puoi giocare con parola attuale! " + response.code);
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante richiesta di playWORDLE! " + e.getMessage());
			System.exit(-1);
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
			System.exit(-1);
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
				System.out.println("Nessuna statistica presente!");
			}
		} catch (IOException e) {
			System.out.println("Errore richiesta statistiche! "+e.getMessage());
			System.exit(-1);
		}
	}


	public static void sendTcpMessage(TcpClientRequestDTO request) throws IOException {
		String json = gson.toJson(request);
		ByteBuffer command = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		socketChannel.write(command);
	}

	public static TcpServerResponseDTO readTcpMessage() throws IOException {

		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		StringBuilder json = new StringBuilder();
		int bytesRead;

		while ((bytesRead = socketChannel.read(buffer)) > 0) {
			// Metto il buffer in modalità lettura
			buffer.flip();
			json.append(StandardCharsets.UTF_8.decode(buffer));
			// Metto il buffer in modalità scrittura e lo pulisco
			buffer.flip();
			buffer.clear();

			// Se i byte letti sono meno della dimensione del buffer allora termino
			if(bytesRead < BUFFER_SIZE)
				break;
		}

		if (json.length() == 0) {
			throw new IOException("Letti 0 bytes, il server potrebbe essere offline(?)");
		}
		return gson.fromJson(json.toString(), TcpServerResponseDTO.class);
	}

	@Override
	public void notifyUsersRank() throws RemoteException {
		System.out.println("Classifica di gioco aggiornata!");
	}
}
