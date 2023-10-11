package client;

import client.entity.CLICommand;
import client.entity.ClientConfig;
import client.enums.ClientModeEnum;
import client.enums.UserCommandEnum;
import client.services.CLIHelper;
import client.worker.MulticastWorker;
import common.dto.*;
import common.entity.WordleGame;
import common.enums.ServerTCPCommandEnum;
import common.interfaces.NotifyEventInterface;
import common.interfaces.ServerRmiInterface;
import server.services.JsonService;

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

import static common.enums.ResponseCodeEnum.*;

public class ClientMain extends RemoteObject implements NotifyEventInterface {

	private static ServerRmiInterface serverRMI;
	private static NotifyEventInterface stub;
	private static MulticastSocket multicastSocket;
	private static Thread multicastThread;
	private static List<UserScore> rank;
	private static final List<WordleGame> sharedGames = new ArrayList<>();
	private static SocketChannel socketChannel;
	private String username = null;
	private ClientModeEnum mode = ClientModeEnum.GUEST_MODE;
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
					// Chiudo il socket TCP con il server
					socketChannel.close();
				} catch (IOException e) {
					System.out.println("Errore durante chiusura socket TCP");
				}

				// Interrompo worker che gestisce il gruppo multicast
				multicastThread.interrupt();

				try {
					// Disiscrivo client da eventi del server
					if (client.username != null) {
						serverRMI.unsubscribeClientFromEvent(client.username);
					}
				} catch (RemoteException e) {
					System.out.println("Errore chiamata RMI unsubscribeClientFromEvent()");
				}
			}
		});

		client.run();
	}

	public ClientMain() {

		super();
		System.out.println("Avvio Wordle game client...");
		ClientConfig.loadConfig();

		// RMI e callback
		try {
			Registry registry = LocateRegistry.getRegistry(ClientConfig.RMI_PORT);
			Remote RemoteObject = registry.lookup(ClientConfig.STUB_NAME);
			serverRMI = (ServerRmiInterface) RemoteObject;
			System.out.println("Lookup registro RMI server riuscito! Stub: " + ClientConfig.STUB_NAME);

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
			socketChannel.connect(new InetSocketAddress(ClientConfig.SERVER_IP, ClientConfig.TCP_PORT));
			System.out.println("Connessione TCP con il server riuscita! "+ClientConfig.SERVER_IP+":"+ClientConfig.TCP_PORT);
		} catch (IOException e) {
			System.out.println("Errore durante connessione TCP al server: "+ e.getMessage());
			System.exit(-1);
		}

		// Inizializza multicast socket
		try {
			multicastSocket = new MulticastSocket(ClientConfig.MULTICAST_PORT);
			InetAddress multicastAddress = InetAddress.getByName(ClientConfig.MULTICAST_IP);
			multicastSocket.joinGroup(multicastAddress);
			System.out.println("Join a gruppo multicast " + ClientConfig.MULTICAST_IP + " avvenuta con successo!");
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
		CLICommand cliCommand = CLIHelper.waitForCommand(username);
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

			default:
				System.out.println("Comando non trovato!");
		}

		CLIHelper.pause();

	}

	private void userMode() {

		CLIHelper.mainMenu();
		CLICommand cliCommand = CLIHelper.waitForCommand(username);
		UserCommandEnum cmd = cliCommand.command;

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
					this.mode = ClientModeEnum.GAME_MODE;
				}
				break;
			}

			case STAT:
				UserStat stat = this.sendMeStatistics();
				CLIHelper.printUserStats(stat);
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
		while (mode == ClientModeEnum.GAME_MODE) {
			CLIHelper.printServerWord(guesses, true);
			System.out.println("Tentativi rimasti: " + remainingAttempts + ". Inserisci una parola:");
			CLICommand cliCommand = CLIHelper.waitForCommand(username);
			UserCommandEnum cmd = cliCommand.command;
			String[] args = cliCommand.args;

			if (cmd == UserCommandEnum.QUIT) {
				this.mode = ClientModeEnum.USER_MODE;
				break;
			}

			CLIHelper.cls();
			this.sendWord(args[0]);
			CLIHelper.printServerWord(guesses, true);
			CLIHelper.pause();
		}
	}

	private void sendWord(String word) {

		if (!canPlayWord) {
			System.out.println("Errore, richiedi prima al server di poter giocare la parola attuale");
			return;
		}

		TcpResponse response = null;
		TcpRequest request = new TcpRequest(ServerTCPCommandEnum.VERIFY_WORD, new String[]{username, word});
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
				System.out.println("Complimenti, hai indovinato la parola! Traduzione: " + response.wordTranslation);
				UserStat stat = this.sendMeStatistics();
				CLIHelper.printUserStats(stat);
				mode = ClientModeEnum.USER_MODE;
				break;
			}

			case GAME_LOST: {
				System.out.println("Tentativi esauriti, hai perso!");
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
				CLIHelper.pause();
				mode = ClientModeEnum.USER_MODE;
				break;

			default:
				System.out.println("Parola non indovinata!");
				guesses = response.userGuess;

		}

	}

	private void login(String username, String password) {

		TcpRequest requestDTO = new TcpRequest(ServerTCPCommandEnum.LOGIN, new String[]{username, password});

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

	private void logout(String username) {

		TcpRequest request = new TcpRequest(ServerTCPCommandEnum.LOGOUT, new String[]{username});

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
	 * Richiedo al server se l'utente puo' iniziare a giocare
	 */
	private void playWORDLE() {
		TcpRequest request = new TcpRequest(ServerTCPCommandEnum.PLAY_WORDLE, new String[]{username});
		try {
			sendTcpMessage(request);

			TcpResponse response = readTcpMessage();
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

	private UserStat sendMeStatistics() {
		TcpRequest request = new TcpRequest(ServerTCPCommandEnum.STAT, new String[]{username});
		try {
			sendTcpMessage(request);

			TcpResponse response = readTcpMessage();
			if(response != null && response.stat != null) {
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
		TcpRequest request = new TcpRequest(ServerTCPCommandEnum.SHARE, new String[]{username});
		try {
			sendTcpMessage(request);
			TcpResponse response = readTcpMessage();
			if (response.code == OK) {
				System.out.println("Statistiche condivise con successo sul gruppo sociale!");
			}
		} catch (IOException e) {
			System.out.println("Errore richiesta condivisione ultima partita! "+e.getMessage());
			System.exit(-1);
		}
	}


	public static void sendTcpMessage(TcpRequest request) throws IOException {
		String json = JsonService.toJson(request);
		ByteBuffer command = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		socketChannel.write(command);
	}

	public static TcpResponse readTcpMessage() throws IOException,RuntimeException {

		final int BUFFER_SIZE = 1024;
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

		TcpResponse response = JsonService.fromJson(json.toString(), TcpResponse.class);
		if(response.code == INTERNAL_SERVER_ERROR) {
			throw new RuntimeException(INTERNAL_SERVER_ERROR.name());
		}
		return response;
	}

	@Override
	public void notifyUsersRank(List<UserScore> newRank) throws RemoteException {
		System.out.println("Ricevuta classifica di gioco dal server!");
		rank = newRank;
	}
}
