package client;

import client.enums.ClientMode;
import client.enums.UserCommand;
import client.services.CLIHelper;
import client.worker.MulticastWorker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.dto.LetterDTO;
import common.dto.TcpClientRequestDTO;
import common.dto.TcpServerResponseDTO;
import common.dto.UserScore;
import common.entity.WordleGame;
import common.enums.ServerTCPCommand;
import common.interfaces.NotifyEventInterface;
import javafx.util.Pair;
import common.interfaces.ServerRmiInterface;
import common.utils.ConfigReader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static common.enums.ResponseCodeEnum.*;

public class ClientMain extends RemoteObject implements NotifyEventInterface {

	private final static int BUFFER_SIZE = 1024;
	private final static Gson gson = new GsonBuilder().create();
	private static String STUB_NAME = "WORDLE-SERVER";
	private static int TCP_PORT;
	private static int RMI_PORT;
	private static String SERVER_IP;
	private static String MULTICAST_IP;
	private static int MULTICAST_PORT;
	private static ServerRmiInterface serverRMI;
	private static NotifyEventInterface stub;
	private static SocketChannel socketChannel;
	private static MulticastSocket multicastSocket;
	private static Thread multicastThread;
	private static String username = null;
	private ClientMode mode = ClientMode.GUEST_MODE;
	private boolean canPlayWord = false;
	private int remainingAttempts;
	private LetterDTO[][] guesses;
	private static List<UserScore> rank;
	private static List<WordleGame> sharedGames = new ArrayList<>();

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
					multicastSocket.leaveGroup(InetAddress.getByName(MULTICAST_IP));
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
			MULTICAST_IP = ConfigReader.readProperty(properties, "app.multicast.ip");
			MULTICAST_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.multicast.port"));
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

		// Inizializza multicast socket
		try {
			multicastSocket = new MulticastSocket(MULTICAST_PORT);
			InetAddress multicastAddress = InetAddress.getByName(MULTICAST_IP);
			multicastSocket.joinGroup(multicastAddress);
			System.out.println("Join a gruppo multicast " + MULTICAST_IP + " avvenuta con successo!");
		} catch (IOException e) {
			System.out.println("Errore durante inizializzazione multicast! " + e.getMessage());
			System.exit(-1);
		}

		// Creo e avvio il thread che rimane in ascolto dei pacchetti multicast in arrivo
		MulticastWorker multicastWorker = new MulticastWorker(multicastSocket, sharedGames);
		multicastThread = new Thread(multicastWorker);
		multicastThread.start();

	}

	/**
	 * Loop sulle varie modalita' del client
	 */
	public void run() {

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
		Pair<UserCommand, String[]> parsedCmd = CLIHelper.waitForCommand();
		UserCommand cmd = parsedCmd.getKey();
		String[] args = parsedCmd.getValue();

		if (cmd == null) {
			System.out.println("Comando non trovato!");
			CLIHelper.pause();
			return;
		}

		// Eseguo un comando
		switch (cmd) {
			case HELP:
				CLIHelper.entryMenu();
				return;

			case QUIT:
				System.exit(0);
				break;

			case LOGIN:
				if (args.length < 2) {
					System.out.println("Comando non valido!");
				} else {
					this.login(args[0], args[1]);
				}
				break;

			case REGISTER:
				if (args.length < 2) {
					System.out.println("Comando non completo");
				} else {
					this.register(args[0], args[1]);
				}
				break;
		}

		CLIHelper.pause();

	}

	private void userMode() {

		CLIHelper.mainMenu();
		UserCommand cmd = CLIHelper.waitForCommand().getKey();

		if (cmd == null) {
			System.out.println("Comando non trovato!");
			CLIHelper.pause();
			return;
		}

		switch (cmd) {
			case HELP: {
				CLIHelper.mainMenu();
				return;
			}

			case LOGOUT: {
				this.logout(username);
				break;
			}

			case PLAY: {
				// Prima di iniziare il gioco devo chiedere al server se l utente puo iniziare o meno
				// TODO capire se fare comando separato per giocare a wordle
				this.playWORDLE();
				if (canPlayWord) {
					this.mode = ClientMode.GAME_MODE;
				}
				break;
			}

			case STAT:
				this.sendMeStatistics();
				break;

			case SHARE:
				System.out.println("Condivido le statistiche con gli altri utenti");
				this.share();
				break;

			case SOCIAL:
				CLIHelper.printUsersGames(sharedGames);
				break;

			case RANK:
				CLIHelper.printRank(rank);
				break;

			case QUIT:
				System.exit(0);

			default:
				System.out.println("Comando non trovato!");
		}

		CLIHelper.pause();
	}

	private void gameMode() {

		System.out.println("GAME MODE! Digita in qualsiasi momento :quit per uscire dalla modalita' gioco!");
		while (mode == ClientMode.GAME_MODE) {
			if(guesses.length > 0) {
				System.out.println("I tuoi tentativi:");
				CLIHelper.printServerWord(guesses);
			}
			System.out.println("Hai ancora " + remainingAttempts + " tentativi rimasti. Inserisci una parola:");
			Pair<UserCommand, String[]> parsedCmd = CLIHelper.waitForCommand();
			UserCommand cmd = parsedCmd.getKey();
			String[] args = parsedCmd.getValue();

			if(cmd == UserCommand.QUIT) {
				this.mode = ClientMode.USER_MODE;
				break;
			}

			CLIHelper.cls();
			this.sendWord(args[0]);
		}
	}

	private void sendWord(String word) {

		if (!canPlayWord) {
			System.out.println("Errore, richiedi prima al server di poter giocare la parola attuale");
			return;
		}

		TcpServerResponseDTO response = null;
		TcpClientRequestDTO request = new TcpClientRequestDTO(ServerTCPCommand.VERIFY_WORD, new String[]{username, word});
		try {
			sendTcpMessage(request);
			response = readTcpMessage();
		} catch (IOException e) {
			System.out.println("Errore durante invio/ricezione guessed word: "+e.getMessage());
			return;
		}

		remainingAttempts = response.remainingAttempts;

		switch (response.code) {

			case GAME_WON:
				System.out.println("Complimenti, hai indovinato la parola! Traduzione: " + response.wordTranslation);
				mode = ClientMode.USER_MODE;
				break;

			case INVALID_WORD_LENGHT:
				System.out.println("Parola troppo lunga o troppo corta, tentativo non valido");
				break;

			case WORD_NOT_IN_DICTIONARY:
				System.out.println("Parola "+word+" non presente nel dizionario, tentativo non valido");
				break;

			case GAME_LOST:
				System.out.println("Tentativi esauriti, hai perso!");
				mode = ClientMode.USER_MODE;
				break;

			case GAME_ALREADY_PLAYED:
				System.out.println("Hai gia' giocato a questa parola!");
				break;

			case NEED_TO_START_GAME:
				System.out.println("Parola cambiata! Devi ripartire da capo!");
				mode = ClientMode.USER_MODE;
				break;

			default:
				System.out.println("Parola non indovinata!");
				guesses = response.userGuess;

		}
		CLIHelper.pause();

	}

	private void login(String username, String password) {

		TcpClientRequestDTO requestDTO = new TcpClientRequestDTO(ServerTCPCommand.LOGIN, new String[]{username, password});

		try {
			sendTcpMessage(requestDTO);

			TcpServerResponseDTO response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Login completato con successo");
				mode = ClientMode.USER_MODE;
				ClientMain.username = username;
				// Iscrive l'utente alle callback dal server
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

		TcpClientRequestDTO request = new TcpClientRequestDTO(ServerTCPCommand.LOGOUT, new String[]{username});

		try {
			sendTcpMessage(request);

			TcpServerResponseDTO response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Logout completato con successo");
				ClientMain.username = null;
				this.mode = ClientMode.GUEST_MODE;
				// Disiscrive l'utente alle callback dal server
				serverRMI.unsubscribeClientToEvent(username);
			} else {
				System.out.println("Errore durante il logout!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il logout");
		}
	}

	/**
	 * Richiedo al server se l'utente puo' iniziare a giocare
	 */
	private void playWORDLE() {
		TcpClientRequestDTO request = new TcpClientRequestDTO(ServerTCPCommand.PLAY_WORDLE, new String[]{username});
		try {
			sendTcpMessage(request);

			TcpServerResponseDTO response = readTcpMessage();
			if (response.code == OK) {
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
		} catch (IllegalArgumentException e) {
			System.out.println("Errore durante la registrazione! " + e.getMessage());
		}
	}

	private void sendMeStatistics() {
		TcpClientRequestDTO request = new TcpClientRequestDTO(ServerTCPCommand.STAT, new String[]{username});
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

	/**
	 * Richiede al server di condividere i risultati dell ultima partita del client sul gruppo sociale
	 */
	private void share() {
		TcpClientRequestDTO request = new TcpClientRequestDTO(ServerTCPCommand.SHARE, new String[]{username});
		try {
			sendTcpMessage(request);
			TcpServerResponseDTO response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Statistiche condivise con successo sul gruppo sociale!");
			}
		} catch (IOException e) {
			System.out.println("Errore richiesta condivisione ultima partita! "+e.getMessage());
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
	public void notifyUsersRank(List<UserScore> newRank) throws RemoteException {
		System.out.println("Ricevuta classifica di gioco dal server!");
		rank = newRank;
	}
}
