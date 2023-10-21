package client;

import client.entity.CLICommand;
import client.entity.ClientConfig;
import client.enums.ClientModeEnum;
import client.enums.UserCommandEnum;
import client.services.CLIHelper;
import client.worker.MulticastWorker;
import common.dto.*;
import common.entity.SharedGame;
import common.enums.AnsiColor;
import common.enums.TCPCommandEnum;
import common.interfaces.NotifyEventInterface;
import common.interfaces.ServerRmiInterface;
import common.utils.WordleLogger;
import server.exceptions.WordleException;
import server.services.JsonService;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.*;
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
import java.util.Arrays;
import java.util.List;

import static common.enums.ResponseCodeEnum.*;

public class ClientMain extends RemoteObject implements NotifyEventInterface {

	private static ServerRmiInterface serverRMI;
	private static NotifyEventInterface stub;
	private static MulticastSocket multicastSocket;
	private static Thread multicastThread;
	private static List<UserScore> rank;
	private static final List<SharedGame> sharedGames = new ArrayList<>();
	private static Socket socket;
	private static final WordleLogger logger = new WordleLogger(ClientMain.class.getName());
	private String username = null;
	private ClientModeEnum mode = ClientModeEnum.GUEST_MODE;
	private int remainingAttempts;
	private LetterDTO[][] guesses;
	private static final String TITLE =
			" __        _____  ____  ____  _     _____    ____ _     ___ _____ _   _ _____ \n" +
			" \\ \\      / / _ \\|  _ \\|  _ \\| |   | ____|  / ___| |   |_ _| ____| \\ | |_   _|\n" +
			"  \\ \\ /\\ / / | | | |_) | | | | |   |  _|   | |   | |    | ||  _| |  \\| | | |  \n" +
			"   \\ V  V /| |_| |  _ <| |_| | |___| |___  | |___| |___ | || |___| |\\  | | |  \n" +
			"    \\_/\\_/  \\___/|_| \\_\\____/|_____|_____|  \\____|_____|___|_____|_| \\_| |_|  \n\n";

	public static void main(String[] argv) {

		System.out.println(TITLE);
		// Inizializza il client
		ClientMain client = new ClientMain();

		// Hook per SIGINT e SIGTERM
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {

				logger.debug("Shutdown Wordle client...");
				try {
					// Chiudo il socket TCP con il server
					socket.close();
				} catch (IOException e) {
					logger.error("Errore durante chiusura socket TCP");
				}

				// Interrompo worker che gestisce il gruppo multicast
				multicastThread.interrupt();

				try {
					// Disiscrivo client da eventi del server
					if (client.username != null) {
						serverRMI.unsubscribeClientFromEvent(client.username);
					}
				} catch (RemoteException e) {
					logger.error("Errore chiamata RMI unsubscribeClientFromEvent()");
				}

				System.out.println("Grazie per aver usato Wordle client! Torna presto!");
			}
		});

		CLIHelper.pause();
		// Avvia il client
		client.run();
	}

	public ClientMain() {

		super();

		// Carico le configurazioni del client
		ClientConfig.loadConfig();

		// RMI e callback
		try {
			Registry registry = LocateRegistry.getRegistry(ClientConfig.RMI_PORT);
			Remote RemoteObject = registry.lookup(ClientConfig.STUB_NAME);
			serverRMI = (ServerRmiInterface) RemoteObject;
			logger.debug("Lookup registro RMI server riuscito! Stub: " + ClientConfig.STUB_NAME);

			// Callback (esporta oggetto)
			stub = (NotifyEventInterface) UnicastRemoteObject.exportObject(this, 0);

		} catch (RemoteException e) {
			logger.error("Errore connessione RMI, controlla che il server sia online: " + e.getMessage());
			System.exit(-1);

		} catch (NotBoundException e) {
			logger.error("Client not bound exception" + e.getMessage());
			System.exit(-1);
		}

		// Inizializza connessione TCP
		try {
			socket = new Socket(ClientConfig.SERVER_IP, ClientConfig.TCP_PORT);
			socket.setSoTimeout(ClientConfig.SOCKET_MS_TIMEOUT);
			logger.debug("Connessione TCP con il server riuscita! "+ClientConfig.SERVER_IP+":"+ClientConfig.TCP_PORT);
		} catch (IOException e) {
			logger.error("Errore durante connessione TCP al server: "+ e.getMessage());
			System.exit(-1);
		}

		// Inizializza multicast socket
		try {
			multicastSocket = new MulticastSocket(ClientConfig.MULTICAST_PORT);
			InetAddress multicastAddress = InetAddress.getByName(ClientConfig.MULTICAST_IP);
			multicastSocket.joinGroup(multicastAddress);
			logger.debug("Join a gruppo multicast " + ClientConfig.MULTICAST_IP + " avvenuta con successo!");
		} catch (IOException e) {
			logger.error("Errore durante inizializzazione multicast! " + e.getMessage());
			System.exit(-1);
		}

		// Creo e avvio il thread che rimane in ascolto dei pacchetti multicast in arrivo
		MulticastWorker multicastWorker = new MulticastWorker(multicastSocket, sharedGames);
		multicastThread = new Thread(multicastWorker);
		multicastThread.start();

		System.out.println("Avvio Wordle client avvenuto con successo!\n");
	}

	/**
	 * Loop sulle varie modalita' del client
	 */
	public void run() {

		while (true) {

			System.out.println(TITLE);
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
			CLIHelper.pause();
		}
	}

	/**
	 * Modalita' per utente non loggato
	 */
	private void guestMode() {

		CLIHelper.entryMenu();
		CLICommand cliCommand = CLIHelper.waitForInput(username, true);
		UserCommandEnum cmd = cliCommand.command;
		String[] args = cliCommand.args;

		if (cmd == null) {
			System.out.println("Comando non trovato!");
			CLIHelper.pause();
			return;
		}

		// Eseguo un comando
		switch (cmd) {

			case HELP:
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

			default:
				System.out.println("Comando non trovato!");
		}

	}

	/**
	 * Modalita' per utente loggato
	 */
	private void userMode() {

		CLIHelper.mainMenu();
		CLICommand cliCommand = CLIHelper.waitForInput(username, true);
		UserCommandEnum cmd = cliCommand.command;

		if (cmd == null) {
			System.out.println("Comando non trovato!");
			return;
		}

		switch (cmd) {

			case HELP: {
				return;
			}

			case LOGOUT: {
				this.logout(username);
				break;
			}

			case PLAY: {
				// Prima di iniziare il gioco devo chiedere al server se l utente puo iniziare o meno
				if (this.playWORDLE()) {
					this.mode = ClientModeEnum.GAME_MODE;
				}
				break;
			}

			case STAT:
				UserStat stat = this.sendMeStatistics();
				CLIHelper.printUserStats(stat);
				break;

			case SHARE:
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
				break;

			default:
				System.out.println("Comando non trovato!");
		}
	}

	/**
	 * Modalita' di gioco
	 */
	private void gameMode() {

		System.out.println("GAME MODE! Digita in qualsiasi momento :quit per uscire dalla modalita' gioco!\n");
		while (mode == ClientModeEnum.GAME_MODE) {
			CLIHelper.printServerWord(guesses, true);
			System.out.println("- Tentativi rimasti: " + remainingAttempts + ". Inserisci una nuova parola!");
			CLICommand cliCommand = CLIHelper.waitForInput(username, false);
			String[] args = cliCommand.args;

			if (args[0].equals(":exit")) {
				this.mode = ClientModeEnum.USER_MODE;
				break;
			}

			CLIHelper.cls();
			this.sendWord(args[0]);
			//CLIHelper.printServerWord(guesses, true);
		}
	}

	/**
	 * Invia una nuova guessed word al server per la verifica
	 * @param word
	 */
	private void sendWord(String word) {

		TcpResponse response;
		TcpRequest request = new TcpRequest(TCPCommandEnum.VERIFY_WORD, username, word);
		try {
			sendTcpMessage(request);
			response = readTcpMessage();
		} catch (IOException e) {
			System.out.println("Errore durante invio/ricezione guessed word: "+e.getMessage());
			return;
		}

		remainingAttempts = response.remainingAttempts;

		switch (response.code) {

			case GAME_WON: {
				CLIHelper.printServerWord(response.userGuess, true);
				System.out.format("+-----------------------------------------+%n");
				System.out.format("+               "+ AnsiColor.GREEN_BACKGROUND +"HAI VINTO! :)"+AnsiColor.RESET+"            +%n");
				System.out.format("+-----------------------------------------+%n");
				System.out.println("Traduzione parola: " + response.wordTranslation);
				UserStat stat = this.sendMeStatistics();
				CLIHelper.printUserStats(stat);
				mode = ClientModeEnum.USER_MODE;
				break;
			}

			case GAME_LOST: {
				CLIHelper.printServerWord(response.userGuess, true);
				System.out.format("+-----------------------------------------+%n");
				System.out.format("+               "+ AnsiColor.RED_BACKGROUND+"HAI PERSO! :("+AnsiColor.RESET+"            +%n");
				System.out.format("+-----------------------------------------+%n");
				UserStat stat = this.sendMeStatistics();
				CLIHelper.printUserStats(stat);
				mode = ClientModeEnum.USER_MODE;
				break;
			}

			case INVALID_WORD_LENGHT:
				System.out.println("Parola troppo lunga o troppo corta, tentativo non valido");
				break;

			case WORD_NOT_IN_DICTIONARY:
				System.out.println("Parola "+word+" non presente nel dizionario, tentativo non valido");
				break;

			case GAME_ALREADY_PLAYED:
				System.out.println("Hai gia' giocato a questa parola!");
				mode = ClientModeEnum.USER_MODE;
				break;

			case NEED_TO_START_GAME:
				System.out.println("Parola cambiata! Devi ripartire da capo!");
				mode = ClientModeEnum.USER_MODE;
				break;

			default:
				guesses = response.userGuess;
		}

	}

	/**
	 * Invia una richiesta di login al server
	 * @param username
	 * @param password
	 */
	private void login(String username, String password) {

		TcpRequest requestDTO = new TcpRequest(TCPCommandEnum.LOGIN, username, password);

		try {
			sendTcpMessage(requestDTO);

			TcpResponse response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Login completato con successo");
				mode = ClientModeEnum.USER_MODE;
				this.username = username;
				// Iscrive l'utente alle callback dal server
				serverRMI.subscribeClientToEvent(username, stub);
			} else if(response.code == ALREADY_LOGGED_IN){
				System.out.println("Utente gia' loggato su altro client, esegui prima la disconnessione!");
			} else {
				System.out.println("Nome utente o password errati!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login! " + e.getMessage());
			System.exit(-1);
		}
	}

	/**
	 * Invia una richiesta di logout al server
	 * @param username
	 */
	private void logout(String username) {

		TcpRequest request = new TcpRequest(TCPCommandEnum.LOGOUT, username);

		try {
			sendTcpMessage(request);

			TcpResponse response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Logout completato con successo");
				this.username = null;
				this.mode = ClientModeEnum.GUEST_MODE;
				// Disiscrive l'utente alle callback dal server
				serverRMI.unsubscribeClientFromEvent(username);
			} else {
				System.out.println("Errore durante il logout!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il logout");
		}
	}

	/**
	 * Invia una richiesta di gioco al server
	 */
	private boolean playWORDLE() {
		TcpRequest request = new TcpRequest(TCPCommandEnum.PLAY_WORDLE, username);
		try {
			sendTcpMessage(request);

			TcpResponse response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Ok, puoi giocare a Wordle!");
				remainingAttempts = response.remainingAttempts;
				guesses = response.userGuess;
				return true;
			} else {
				System.out.println("Errore, non puoi giocare con parola attuale! " + response.code);
				return false;
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante richiesta di playWORDLE! " + e.getMessage());
			System.exit(-1);
			return false;
		}
	}

	/**
	 * Chiama la RMI del server per poter registrare un utente.
	 * @param username
	 * @param password
	 */
	private void register(String username, String password) {

		try {
			serverRMI.register(username, password);
			System.out.println("Complimenti! Registrazione completata con successo!");
		} catch (RemoteException e) {
			System.out.println("Errore sconosciuto durante la registrazione!");
		} catch (WordleException e) {
			System.out.println("Errore! " + e.getMessage());
		}
	}

	/**
	 * Richiede le statistiche personali al server
	 * @return
	 */
	private UserStat sendMeStatistics() {
		TcpRequest request = new TcpRequest(TCPCommandEnum.STAT, username);
		try {
			sendTcpMessage(request);

			TcpResponse response = readTcpMessage();
			if (response.stat != null) {
				return response.stat;
			}
		} catch (IOException | RuntimeException e) {
			System.out.println("Errore richiesta statistiche! "+e.getMessage());
		}
		return null;
	}

	/**
	 * Richiede al server di condividere i risultati dell ultima partita del client sul gruppo sociale
	 */
	private void share() {
		TcpRequest request = new TcpRequest(TCPCommandEnum.SHARE, username);
		try {
			sendTcpMessage(request);
			TcpResponse response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Ultima partita condivisa con successo sul gruppo sociale!");
			}
		} catch (IOException e) {
			System.out.println("Errore richiesta condivisione ultima partita! "+e.getMessage());
			System.exit(-1);
		}
	}

	/**
	 * Funzione wrapper per inviare la richiesta al server in formato JSON
	 * @param request
	 * @throws IOException
	 */
	public static void sendTcpMessage(TcpRequest request) throws IOException {
		String json = JsonService.toJson(request);
		Writer writer = new OutputStreamWriter(socket.getOutputStream());
		writer.write(json);
		writer.flush();
	}

	/**
	 * Legge dal server la risposta JSON e la converte in un oggetto java
	 * @return
	 * @throws IOException
	 * @throws RuntimeException
	 */
	public static TcpResponse readTcpMessage() throws IOException, RuntimeException {

		final int BUFFER_SIZE = 1024;
		byte[] buffer = new byte[BUFFER_SIZE];
		StringBuilder json = new StringBuilder();
		int bytesRead;
		BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());

		while ((bytesRead = inputStream.read(buffer)) > 0) {
			json.append(new String(Arrays.copyOfRange(buffer, 0, bytesRead)));
			System.out.println(new String(Arrays.copyOfRange(buffer, 0, bytesRead)));
			if (bytesRead < BUFFER_SIZE) {
				break;
			}
		}

		if (json.length() == 0) {
			throw new IOException("Letti 0 bytes, il server potrebbe essere offline(?)");
		}

		TcpResponse response = JsonService.fromJson(json.toString(), TcpResponse.class);
		if(response.code == INTERNAL_SERVER_ERROR) {
			throw new RuntimeException(INTERNAL_SERVER_ERROR.name());
		}
		return response;
	}

	/**
	 * RMI callback che viene invocata dal server in caso di cambiamenti nelle prime 3 posizioni della classifica
	 * @param newRank
	 * @throws RemoteException
	 */
	@Override
	public void notifyUsersRank(List<UserScore> newRank) throws RemoteException {
		logger.debug("Ricevuta classifica di gioco dal server!");
		rank = newRank;
	}
}
