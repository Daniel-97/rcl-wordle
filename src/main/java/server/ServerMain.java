package server;

import common.dto.*;
import common.enums.AnsiColor;
import common.utils.WordleLogger;
import server.entity.ServerConfig;
import server.entity.User;
import common.interfaces.NotifyEventInterface;
import common.interfaces.ServerRmiInterface;
import server.entity.WordleGameState;
import server.exceptions.WordleException;
import server.services.JsonService;
import server.services.UserService;
import server.services.WordleGameService;
import server.tasks.RequestTask;
import server.tasks.WordExtractorTask;
import server.thread.ServerShutdownHook;

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
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.*;

import static common.enums.ResponseCodeEnum.*;

public class ServerMain extends RemoteObject implements ServerRmiInterface {

	private static final WordleLogger logger = new WordleLogger(ServerMain.class.getName());
	public static ThreadPoolExecutor poolExecutor;
	public static ScheduledExecutorService wordUpdateExecutor;
	public static ServerSocketChannel socketChannel;
	public static MulticastSocket multicastSocket;
	private static final HashMap<String, NotifyEventInterface> clients = new HashMap<>();
	private static Selector selector;
	private final UserService userService;
	private final WordleGameService wordleGameService;

	private static final String TITLE =
			" __        _____  ____  ____  _     _____   ____  _____ ______     _______ ____  \n" +
			" \\ \\      / / _ \\|  _ \\|  _ \\| |   | ____| / ___|| ____|  _ \\ \\   / / ____|  _ \\ \n" +
			"  \\ \\ /\\ / / | | | |_) | | | | |   |  _|   \\___ \\|  _| | |_) \\ \\ / /|  _| | |_) |\n" +
			"   \\ V  V /| |_| |  _ <| |_| | |___| |___   ___) | |___|  _ < \\ V / | |___|  _ < \n" +
			"    \\_/\\_/  \\___/|_| \\_\\____/|_____|_____| |____/|_____|_| \\_\\ \\_/  |_____|_| \\_\\\n\n";

	public static void main(String[] argv) {

		System.out.println(TITLE);

		// Inizializza il server
		ServerMain server = new ServerMain();
		// Hook per SIGINT e SIGTERM
		Runtime.getRuntime().addShutdownHook(new ServerShutdownHook());

		// Avvio il server
		server.listen();
	}

	public ServerMain() {

		logger.info("Avvio Wordle game server...");
		// Carico le configurazioni
		ServerConfig.loadConfig();

		// Inizializzo i servizi
		this.userService = UserService.getInstance();
		this.wordleGameService = WordleGameService.getInstance();

		// Avvio il thread che si occupera' di estrarre la nuova parola
		wordUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
		// Calcolo i minuti rimanenti della parola precedentemente estratta
		long remainingTime = wordleGameService.getWordRemainingMinutes();
		wordUpdateExecutor.scheduleAtFixedRate(new WordExtractorTask(), remainingTime, ServerConfig.WORD_TIME_MINUTES, TimeUnit.MINUTES);
		// Stampo la parola attuale nel caso in cui non sia ancora scaduta
		if (remainingTime > 0) {
			WordleGameState state = this.wordleGameService.getState();
			logger.info("Parola attuale: " + AnsiColor.WHITE_BOLD + state.word + AnsiColor.RESET + ", traduzione: " + state.translation);
		}

		// Inizializza RMI server
		try {
			// Esportazione oggetto
			ServerRmiInterface stub = (ServerRmiInterface) UnicastRemoteObject.exportObject(this, 0);
			// Creazione registry
			LocateRegistry.createRegistry(ServerConfig.RMI_PORT);
			Registry registry = LocateRegistry.getRegistry(ServerConfig.RMI_PORT);
			// Pubblicazione dello stub nel registry
			registry.bind(ServerConfig.STUB_NAME, stub);

			logger.info("RMI server in ascolto sulla porta " + ServerConfig.RMI_PORT);
		} catch (AlreadyBoundException e){
			logger.error("RMI already bind exception: " + e.getMessage());
			System.exit(-1);
		} catch (RemoteException e){
			logger.error("RMI remote exception: " + e.getMessage());
			System.exit(-1);
		}

		// Inizializza TCP server
		try {
			//Creo il channel
			socketChannel = ServerSocketChannel.open();
			ServerSocket socket = socketChannel.socket();
			socket.bind(new InetSocketAddress(ServerConfig.TCP_PORT));
			//Configuro il channel in modo da essere non bloccante
			socketChannel.configureBlocking(false);

			selector = Selector.open();
			socketChannel.register(selector, SelectionKey.OP_ACCEPT, null);
		} catch (IOException e) {
			System.exit(-1);
		}

		// Inizializza multicast socket
		try {
			multicastSocket = new MulticastSocket(ServerConfig.MULTICAST_PORT);
		} catch (IOException e) {
			logger.error("Errore durante inizializzazione multicast! " + e.getMessage());
			System.exit(-1);
		}

		// Inizializza thread pool executor
		int coreCount = Runtime.getRuntime().availableProcessors();
		logger.debug("Creo una cached thread pool con dimensione massima " + coreCount*2);
		poolExecutor = new ThreadPoolExecutor(0, coreCount*2, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000));

	}

	/**
	 * Funzione principale del server, in ascolto di nuove connessioni, socket pronti in uscita e in ingresso
	 */
	public void listen() {

		// While in ascolto sui socket
		while (true) {

			try {
				// Bloccate, si ferma fino a quando almeno un canale non e' pronto
				selector.select();
			} catch (IOException e) {
				logger.error("Errore durante la selezione di un canale!");
				System.exit(-1);
			}

			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			// Iteratore delle chiavi
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

			// Fino a che ci sono canali pronti continuo a ciclare
			while (keyIterator.hasNext()) {
				SelectionKey key = keyIterator.next();
				// Rimuovo la chiave dall'iteratore, il selector non rimuove automaticamente le chiavi
				keyIterator.remove();

				try {

					// Nuova connessione dal client
					if (key.isAcceptable()) {

						ServerSocketChannel server = (ServerSocketChannel) key.channel();
						// Accetto la nuova connessione
						SocketChannel client = server.accept();
						logger.debug("Accettata nuova connessione TCP da client " + client.getRemoteAddress());
						client.configureBlocking(false);
						// Aggiungo il client al selector su operazioni di READ
						client.register(selector, SelectionKey.OP_READ, null);
					}

					// Canale pronto per la lettura
					else if (key.isReadable()) {

						SocketChannel client = (SocketChannel) key.channel();
						SocketAddress clientAddress = client.getRemoteAddress();

						TcpRequest clientMessage;
						try {
							// Leggo il messaggio in arrivo dal client
							clientMessage = ServerMain.readTcpMessage(client);

							// Se il messaggio e' vuoto potrebbe essere causato da una possibile disconnessione forzata del client
							if (clientMessage == null) {
								logger.warn("Disconnessione forzata del client " + clientAddress);
								// Effettuo la disconnessione dell'utente utilizzando il suo hash code
								userService.logout(clientAddress.hashCode());
								client.close();
								break;
							}

							// Registro il selettore per operazione di lettura
							SelectionKey writeKey = client.register(selector, SelectionKey.OP_WRITE);
							// Metto in coda alla threadpool il nuovo task da gestire
							poolExecutor.submit(new RequestTask(writeKey, clientMessage));

						} catch (Exception e) {
							logger.error("Impossibile gestire nuova richiesta "+e);
							// In caso di errore imprevisto invio un codice di errore generico al client
							ServerMain.sendTcpMessage(client, new TcpResponse(INTERNAL_SERVER_ERROR));
						}
					}

					// Canale pronto per la scrittura. Il canale potrebbe essere pronto ma il thread potrebbe non aver
					// ancora messo nell'attachment la risposta da inviare al client
					else if (key.isWritable() && key.attachment() != null) {

						SocketChannel client = (SocketChannel) key.channel();
						TcpResponse response = (TcpResponse) key.attachment();

						if (response != null) {
							// Invio la risposta al client
							sendTcpMessage(client, response);
							// Registro nuovamente il client per un operazione di lettura
							client.register(selector, SelectionKey.OP_READ, null);
						}
					}

				} catch (IOException ioe) {
					// Errore imprevisto
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException e) {
						logger.error("Errore imprevisto! " + e);
					}
				}

			}

		}

	}

	/**
	 * Funzione RMI per registrare un nuovo utente
	 * @param username
	 * @param password
	 * @throws RemoteException
	 * @throws IllegalArgumentException
	 */
	@Override
	public synchronized void register(String username, String password) throws RemoteException, WordleException {

		// Controllo parametri
		if (username.isEmpty()) {
			throw new WordleException(USERNAME_REQUIRED);
		}

		if (password.isEmpty()) {
			throw new WordleException(PASSWORD_REQUIRED);
		}

		// Controllo se gia' esiste un utente con lo stesso username
		User user = this.userService.getUser(username);
		if (user != null) {
			throw new WordleException(USERNAME_ALREADY_USED);
		}

		// Aggiungo nuovo utente al sistema
		try {
			User newUser = new User(username, password);
			this.userService.addUser(newUser);
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			logger.error("Impossibile registrare nuovo utente " + e);
			throw new RemoteException(INTERNAL_SERVER_ERROR.name());
		}
	}

	@Override
	public synchronized void subscribeClientToEvent(String username, NotifyEventInterface eventInterface) throws RemoteException {

		// Se utente gia' presente elimino vecchia subscription
		if (clients.get(username) != null) {
			clients.remove(username);
		}

		// Aggiungo utente alla lista di utenti che vogliono essere notificati degli eventi asincroni
		clients.put(username, eventInterface);
		logger.success("Utente " + username + " iscritto per eventi asincroni!");
		// Invio al client la classifica attuale
		eventInterface.notifyUsersRank(this.userService.getRank());
	}

	@Override
	public synchronized void unsubscribeClientFromEvent(String username) throws RemoteException {
		clients.remove(username);
		logger.info("Utente " + username + " disiscritto da eventi asincroni!");
	}

	/**
	 * Invia un messaggio JSON al client
	 * @param socketChannel
	 * @param request
	 * @throws IOException
	 */
	public static void sendTcpMessage(SocketChannel socketChannel, TcpResponse request) throws IOException {

		String json = JsonService.toJson(request);
		ByteBuffer jsonRequest = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		socketChannel.write(jsonRequest);
	}

	/**
	 * Riceve un messaggio in formato JSON dal client e lo converte in un oggetto TcpRequest
	 * @param socketChannel
	 * @return
	 * @throws IOException
	 */
	public static TcpRequest readTcpMessage(SocketChannel socketChannel) throws IOException {

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

		return JsonService.fromJson(json.toString(), TcpRequest.class);
	}

	/**
	 * Invia il messaggio specificato sul gruppo di multicast
	 * @param message
	 */
	public static void sendMulticastMessage(String message) throws IOException {

		byte[] data = message.getBytes(StandardCharsets.UTF_8);
		InetAddress multicastAddress = InetAddress.getByName(ServerConfig.MULTICAST_IP);
		DatagramPacket packet = new DatagramPacket(data, data.length, multicastAddress, ServerConfig.MULTICAST_PORT);
		multicastSocket.send(packet);
	}

	/**
	 * Notifica la classifica attuale di gioco ai vari client che si sono iscritti
	 */
	public static void notifyRankToClient(List<UserScore> rank) {
		for(Map.Entry<String, NotifyEventInterface> client: clients.entrySet()) {
			try {
				client.getValue().notifyUsersRank(rank);
			} catch (RemoteException e) {
				logger.error("Errore durante invio aggiornamento classifica a utente "+client.getKey()+". " + e.getMessage());
			}
		}
	}
}