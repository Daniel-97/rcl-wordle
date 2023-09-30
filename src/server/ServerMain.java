package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.dto.LetterDTO;
import common.dto.TcpClientRequestDTO;
import common.dto.TcpServerResponseDTO;
import common.dto.UserStat;
import server.entity.User;
import server.entity.WordleGame;
import common.enums.ResponseCodeEnum;
import server.exceptions.WordleException;
import server.interfaces.NotifyEvent;
import server.interfaces.ServerRMI;
import server.services.UserService;
import server.services.WordleGameService;
import common.utils.ConfigReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

public class ServerMain extends RemoteObject implements ServerRMI {

	private final static String STUB_NAME = "WORDLE-SERVER";
	private final static Gson gson = new GsonBuilder().create();
	private final static int BUFFER_SIZE = 1024;
	private static int TCP_PORT;
	private static int RMI_PORT;
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
		} catch (NoSuchFieldException e) {
			System.exit(-1);
		} catch (NumberFormatException e) {
			System.out.println("Parametro di configurazione malformato! " + e.getMessage());
			System.exit(-1);
		}

		// Inizializzo i servizi
		this.userService = new UserService();
		this.wordleGameService = new WordleGameService(userService);

		// Inizializza RMI server
		try {
			// Esportazione oggetto
			ServerRMI stub = (ServerRMI) UnicastRemoteObject.exportObject(this, 0);
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

	}

	public void start() {

		ServerSocket socket = null;
		ServerSocketChannel socketChannel = null;
		Selector selector = null;

		//todo Spostare questa parte nel costruttore
		try {

			//Creo il channel
			socketChannel = ServerSocketChannel.open();
			socket = socketChannel.socket();
			socket.bind(new InetSocketAddress(TCP_PORT));
			//Configuro il channel in modo da essere non bloccante
			socketChannel.configureBlocking(false);

			selector = Selector.open();
			//TODO gestire meglio le eccezioni gestite dalla register
			SelectionKey key =  socketChannel.register(selector, SelectionKey.OP_ACCEPT, null);
		} catch (IOException e) {
			System.exit(-1);
		}

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

								// Aggiunto gioco al giocatore attuale
								if(lastGame == null || !lastGame.word.equals(wordleGameService.getGameWord())) {
									user.newGame(wordleGameService.getGameWord());
									response.success = true;
								} else if (lastGame.word.equals(wordleGameService.getGameWord()) && !lastGame.finished) {
									response.success = true;
								} else {
									response.success = false;
								}

								sendTcpMessage(client, response);
								break;
							}

							case "sendWord": {
								String username = clientMessage.arguments[0];
								String word = clientMessage.arguments[1];
								User user = this.userService.getUser(username);
								TcpServerResponseDTO response = new TcpServerResponseDTO();

								if(word.length() > WordleGameService.WORD_LENGHT || word.length() < WordleGameService.WORD_LENGHT){
									response.success = false;
									response.code = ResponseCodeEnum.INVALID_WORD_LENGHT;
									sendTcpMessage(client, response);
									break;
								}

								WordleGame game = user.getLastGame();
								if(game.getRemainingAttempts() == 0) {
									response.success = false;
									response.code = ResponseCodeEnum.GAME_LOST;
								} else {
									game.won = word.equals(game.word);
									game.attempts++;
									response.success = true;

									if(game.won) {
										response.code = ResponseCodeEnum.GAME_WON;
									} else {
										LetterDTO[] guess = wordleGameService.hintWord(word);
										System.out.println(Arrays.toString(guess));
										game.addGuess(guess);
										response.userGuess = game.guess;
									}
								}

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
	public void subscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}

	@Override
	public void unsubscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}

	public static void sendTcpMessage(SocketChannel socket, TcpServerResponseDTO request) throws IOException {

		String json = gson.toJson(request);
		ByteBuffer command = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		socket.write(command);
	}

	public static TcpClientRequestDTO readTcpMessage(SocketChannel socket) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		StringBuilder json = new StringBuilder();
		int bytesRead = 0;

		while ((bytesRead = socket.read(buffer)) > 0) {
			// Sposto il buffer il lettura
			buffer.flip();
			// Leggo i dati dal buffer
			json.append(StandardCharsets.UTF_8.decode(buffer));
			// Pulisco il buffer
			buffer.clear();
			// Sposto il buffer in scrittura
			buffer.flip();
		}

		return gson.fromJson(json.toString(), TcpClientRequestDTO.class);
	}
}