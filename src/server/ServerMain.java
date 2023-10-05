package server;

import common.dto.*;
import server.entity.User;
import common.entity.WordleGame;
import common.enums.ResponseCodeEnum;
import server.exceptions.WordleException;
import common.interfaces.NotifyEventInterface;
import common.interfaces.ServerRmiInterface;
import server.services.JsonService;
import server.services.UserService;
import server.services.WordleGameService;
import common.utils.ConfigReader;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class ServerMain extends RemoteObject implements ServerRmiInterface {

	// Configuration
	private final static String STUB_NAME = "WORDLE-SERVER";
	private static int TCP_PORT;
	private static int RMI_PORT;
	private static String MULTICAST_IP;
	private static int MULTICAST_PORT;
	private static int WORD_TIME_MINUTES;
	private static final Map<String, NotifyEventInterface> clients = new HashMap<>();
	private static Selector selector;
	private static ServerSocketChannel socketChannel;
	private static MulticastSocket multicastSocket;

	// Services
	private final UserService userService;
	private final WordleGameService wordleGameService;

	private static final String TITLE =
			" __        _____  ____  ____  _     _____   ____  _____ ______     _______ ____  \n" +
			" \\ \\      / / _ \\|  _ \\|  _ \\| |   | ____| / ___|| ____|  _ \\ \\   / / ____|  _ \\ \n" +
			"  \\ \\ /\\ / / | | | |_) | | | | |   |  _|   \\___ \\|  _| | |_) \\ \\ / /|  _| | |_) |\n" +
			"   \\ V  V /| |_| |  _ <| |_| | |___| |___   ___) | |___|  _ < \\ V / | |___|  _ < \n" +
			"    \\_/\\_/  \\___/|_| \\_\\____/|_____|_____| |____/|_____|_| \\_\\ \\_/  |_____|_| \\_\\";

	public static void main(String[] argv) {

		System.out.println(TITLE);

		// Inizializza il server
		ServerMain server = new ServerMain();

		// Thread in ascolto di SIGINT e SIGTERM
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown Wordle server...");
				server.userService.saveUsers();
				server.wordleGameService.saveState();
				multicastSocket.close();
				try {socketChannel.close();} catch (IOException ignore) {}
			}
		});

		// Server start
		server.start();
	}

	public ServerMain() {

		System.out.println("Avvio Wordle game server...");

		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig();
		try {
			TCP_PORT = Integer.parseInt(ConfigReader.readProperty(properties,"app.tcp.port"));
			RMI_PORT = Integer.parseInt(ConfigReader.readProperty(properties,"app.rmi.port"));
			MULTICAST_IP = ConfigReader.readProperty(properties, "app.multicast.ip");
			MULTICAST_PORT = Integer.parseInt(ConfigReader.readProperty(properties, "app.multicast.port"));
			WORD_TIME_MINUTES = Integer.parseInt(ConfigReader.readProperty(properties, "app.wordle.word.time.minutes"));
		} catch (NoSuchFieldException e) {
			System.out.println("Parametro di configurazione non trovato! " + e.getMessage());
			System.exit(-1);
		} catch (NumberFormatException e) {
			System.out.println("Parametro di configurazione malformato! " + e.getMessage());
			System.exit(-1);
		}

		// Inizializzo i servizi
		this.userService = new UserService();
		this.wordleGameService = new WordleGameService(userService, WORD_TIME_MINUTES);

		// Inizializza RMI server
		try {
			// Esportazione oggetto
			ServerRmiInterface stub = (ServerRmiInterface) UnicastRemoteObject.exportObject(this, 0);
			// Creazione registry
			LocateRegistry.createRegistry(RMI_PORT);
			Registry registry = LocateRegistry.getRegistry(RMI_PORT);
			// Pubblicazione dello stub nel registry
			registry.bind(ServerMain.STUB_NAME, stub);

			System.out.println("RMI server in ascolto sulla porta " + RMI_PORT);
		} catch (AlreadyBoundException e){
			System.out.println("RMI already bind exception: " + e.getMessage());
			System.exit(-1);
		} catch (RemoteException e){
			System.out.println("RMI remote exception: " + e.getMessage());
			System.exit(-1);
		}

		// Inizializza TCP server
		try {
			//Creo il channel
			socketChannel = ServerSocketChannel.open();
			ServerSocket socket = socketChannel.socket();
			socket.bind(new InetSocketAddress(TCP_PORT));
			//Configuro il channel in modo da essere non bloccante
			socketChannel.configureBlocking(false);

			selector = Selector.open();
			socketChannel.register(selector, SelectionKey.OP_ACCEPT, null);
		} catch (IOException e) {
			System.exit(-1);
		}

		// Inizializza multicast socket
		try {
			multicastSocket = new MulticastSocket(MULTICAST_PORT);
		} catch (IOException e) {
			System.out.println("Errore durante inizializzazione multicast! " + e.getMessage());
			System.exit(-1);
		}

	}

	public void start() {

		// While in ascolto sui socket
		while (true) {

			try {
				// Bloccate, si ferma fino a quando almeno un canale non e' pronto
				selector.select();
			} catch (IOException e) {
				System.out.println("Errore durante la selezione di un canale!");
				System.exit(-1);
			}

			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			// Iteratore
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
			// Fino a che ci sono canali pronti continuo a ciclare
			while (keyIterator.hasNext()) {
				SelectionKey key = keyIterator.next();
				// Rimuovo la chiave dall'iteratore, il selector non rimuove automaticamente le chiavi
				keyIterator.remove();

				try {
					//Connessione accettata da client
					if (key.isAcceptable()) {
						ServerSocketChannel server = (ServerSocketChannel) key.channel();
						// Accetto la nuova connessione
						SocketChannel client = server.accept();
						System.out.println("Accettata nuova connessione TCP da client " + client.getRemoteAddress());
						client.configureBlocking(false);
						// Aggiungo il client al selector su operazioni di READ
						client.register(selector, SelectionKey.OP_READ, null);
					}

					// Connessione con il client avvenuta
					else if (key.isConnectable()) {
						System.out.println("Nuova connessione stabilita con client");
					}

					// Canale pronto per la lettura
					else if (key.isReadable()) {

						//System.out.println("Canale pronto per la lettura");
						SocketChannel client = (SocketChannel) key.channel();
						TcpClientRequestDTO clientMessage = readTcpMessage(client);
						SocketAddress clientAddress = client.getRemoteAddress();

						if (clientMessage == null) {
							System.out.println("Disconnessione forzata del client " + clientAddress);
							client.close();
							break;
						}

						System.out.println("Nuovo messaggio da client "+clientAddress+":"+ clientMessage);

						switch (clientMessage.command) {

							case "login": {
								// TODO controllare se gli argomenti ci sono o meno
								boolean success = this.userService.login(clientMessage.arguments[0], clientMessage.arguments[1]);
								sendTcpMessage(client, new TcpServerResponseDTO(success, null));
								break;
							}

							case "logout": {
								boolean success = this.userService.logout(clientMessage.arguments[0]);
								sendTcpMessage(client, new TcpServerResponseDTO(success, null));
								break;
							}

							case "playWORDLE": {
								User user = this.userService.getUser(clientMessage.arguments[0]);
								WordleGame lastGame = user.getLastGame();
								TcpServerResponseDTO response = new TcpServerResponseDTO();

								// TODO migliorare questo codice
								// Aggiunto gioco al giocatore attuale
								if (lastGame == null || !lastGame.word.equals(wordleGameService.getGameWord())) {
									user.newGame(wordleGameService.getGameWord());
									response.success = true;
									response.remainingAttempts = user.getLastGame().getRemainingAttempts();
									response.userGuess = user.getLastGame().getGuess();
								} else if (lastGame.word.equals(wordleGameService.getGameWord()) && !lastGame.finished) {
									response.success = true;
									response.remainingAttempts = user.getLastGame().getRemainingAttempts();
									response.userGuess = user.getLastGame().getGuess();
								} else {
									response.success = false;
								}

								sendTcpMessage(client, response);
								break;
							}

							case "sendWord": {
								String username = clientMessage.arguments[0];
								String clientWord = clientMessage.arguments[1];
								User user = this.userService.getUser(username);
								WordleGame lastGame = user.getLastGame();
								TcpServerResponseDTO response = new TcpServerResponseDTO();
								response.remainingAttempts = lastGame.getRemainingAttempts();

								// Ultimo gioco dell'utente e' diverso dalla parola attualmente estratta
								if (!lastGame.word.equals(wordleGameService.getGameWord())) {
									sendTcpMessage(client, new TcpServerResponseDTO(false, ResponseCodeEnum.NEED_TO_START_GAME));
									break;
								}

								// Ultimo gioco dell'utente corrisponde alla parola attuale ed ha gia' completato il gioco
								else if (lastGame.finished) {
									sendTcpMessage(client, new TcpServerResponseDTO(false, ResponseCodeEnum.GAME_ALREADY_PLAYED));
									break;
								}

								// Utente ha inviato parola di lunghezza errata
								else if (clientWord.length() > WordleGameService.WORD_LENGHT || clientWord.length() < WordleGameService.WORD_LENGHT){
									sendTcpMessage(client, new TcpServerResponseDTO(false, ResponseCodeEnum.INVALID_WORD_LENGHT));
									break;
								}

								// Utente ha mandato parola che non si trova nel dizionario
								else if (!wordleGameService.isWordInDict(clientWord)) {
									sendTcpMessage(client, new TcpServerResponseDTO(false, ResponseCodeEnum.WORD_NOT_IN_DICTIONARY));
									break;
								}

								// Aggiungo il tentativo effettuato dall'utente
								LetterDTO[] guess = wordleGameService.hintWord(clientWord);
								lastGame.addGuess(guess);
								System.out.println("Aggiunto guess per parola " + clientWord + " dell'utente " + username);
								// Aggiorno lo status del gioco
								lastGame.won = clientWord.equals(lastGame.word);
								lastGame.finished = lastGame.getRemainingAttempts() == 0 || lastGame.won;

								// Se la partita e' finita lo comunico al client
								if(lastGame.finished) {
									TcpServerResponseDTO res = new TcpServerResponseDTO();
									res.success = true;
									res.code = lastGame.won ? ResponseCodeEnum.GAME_WON : ResponseCodeEnum.GAME_LOST;
									res.wordTranslation = wordleGameService.getWordTranslation();
									sendTcpMessage(client, res);
									// TODO notificare questo cambiamento solo se ci sono aggiornamenti nei primi 3 posti della classifica
									notifyRankToClient(userService.getRank());
									break;
								}

								response.success = true;
								response.userGuess = lastGame.getGuess();
								sendTcpMessage(client, response);
								break;
							}

							case "stat": {
								String username = clientMessage.arguments[0];
								User user = this.userService.getUser(username);
								// Todo controllare che utente esista davvero
								UserStat stat = user.getStat();
								TcpServerResponseDTO response = new TcpServerResponseDTO();
								response.stat = stat;
								sendTcpMessage(client, response);
								break;
							}

							case "share": {
								String username = clientMessage.arguments[0];
								User user = this.userService.getUser(username);

								if (user == null) {
									sendTcpMessage(client, new TcpServerResponseDTO(false, ResponseCodeEnum.INVALID_USERNAME));
									break;
								}

								WordleGame lastGame = user.getLastGame();
								if (lastGame == null) {
									sendTcpMessage(client, new TcpServerResponseDTO(false, ResponseCodeEnum.NO_GAME_TO_SHARE));
									break;
								}

								// Invio ultima partita dell'utente su gruppo multicast
								System.out.println("Invio ultima partita dell'utente " + username + " sul gruppo sociale...");
								// Invio solamente le informazioni che mi interessano non tutto l'oggetto
								WordleGame game = new WordleGame();
								game.attempts = lastGame.attempts;
								game.startedAt = lastGame.startedAt;
								sendMulticastMessage(JsonService.toJson(game));
								sendTcpMessage(client, new TcpServerResponseDTO(true));
								break;
							}
							default:
								System.out.println("Comando sconosciuto("+clientMessage.command+") ricevuto da "+clientAddress);
						}

					}

					// Canale pronto per la scrittura
					else if (key.isWritable()) {
						System.out.println("Canale pronto per la scrittura");
					}

				} catch (IOException ioe) {
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException ignored) {}
				}

			}

		}

	}

	@Override
	public void register(String username, String password) throws RemoteException, WordleException {

		// Controllo parametri
		if (username.isEmpty()) {
			throw new IllegalArgumentException(ResponseCodeEnum.USERNAME_REQUIRED.name());
		}

		if (password.isEmpty()) {
			throw new IllegalArgumentException(ResponseCodeEnum.PASSWORD_REQUIRED.name());
		}

		// Aggiungo nuovo utente al sistema
		User user = new User(username, password);
		this.userService.addUser(user);
	}

	@Override
	public synchronized void subscribeClientToEvent(String username, NotifyEventInterface eventInterface) throws RemoteException {

		// Se utente gia' presente elimino vecchia subscription
		if (clients.get(username) != null) {
			clients.remove(username);
		}

		// Aggiungo utente alla lista di utenti che vogliono essere notificati degli eventi asincroni
		clients.put(username, eventInterface);
		System.out.println("Utente " + username + " iscritto per eventi asincroni!");
		// Invio al client la classifica attuale
		eventInterface.notifyUsersRank(this.userService.getRank());
	}

	@Override
	public synchronized void unsubscribeClientToEvent(String username) throws RemoteException {
		clients.remove(username);
		System.out.println("Utente " + username + " disiscritto da eventi asincroni!");
	}

	public static void sendTcpMessage(SocketChannel socket, TcpServerResponseDTO request) throws IOException {

		String json = JsonService.toJson(request);
		ByteBuffer command = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		socket.write(command);
	}

	public static TcpClientRequestDTO readTcpMessage(SocketChannel socketChannel) throws IOException {

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

		return JsonService.fromJson(json.toString(), TcpClientRequestDTO.class);
	}

	/**
	 * Invia il messaggio specificato sul gruppo di multicast
	 * @param message
	 */
	public static void sendMulticastMessage(String message) throws IOException {

		byte[] data = message.getBytes(StandardCharsets.UTF_8);
		InetAddress multicastAddress = InetAddress.getByName(MULTICAST_IP);
		DatagramPacket packet = new DatagramPacket(data, data.length, multicastAddress, MULTICAST_PORT);
		multicastSocket.send(packet);
	}

	/**
	 * Notifica la classifica attuale di gioco ai vari client che si sono iscritti
	 */
	private static void notifyRankToClient(List<UserScore> rank) {
		for(Map.Entry<String, NotifyEventInterface> client: clients.entrySet()) {
			try {
				client.getValue().notifyUsersRank(rank);
			} catch (RemoteException e) {
				System.out.println("Errore durante invio aggiornamento classifica a utente "+client.getKey()+". " + e.getMessage());
			}
		}
	}
}