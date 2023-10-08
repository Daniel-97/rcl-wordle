package server;

import common.dto.*;
import server.entity.User;
import common.interfaces.NotifyEventInterface;
import common.interfaces.ServerRmiInterface;
import server.services.JsonService;
import server.services.UserService;
import server.services.WordleGameService;
import common.utils.ConfigReader;
import server.tasks.RequestTask;

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

	// Configuration
	private final static String STUB_NAME = "WORDLE-SERVER";
	private static int TCP_PORT;
	private static int RMI_PORT;
	private static String MULTICAST_IP;
	private static int MULTICAST_PORT;
	private static int WORD_TIME_MINUTES;
	private static ThreadPoolExecutor poolExecutor;
	// TODO, non thread safe adesso!
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
				poolExecutor.shutdown();
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
		this.userService = UserService.getInstance();
		this.wordleGameService = WordleGameService.getInstance();
		this.wordleGameService.init(WORD_TIME_MINUTES);

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

		// Inizializza thread pool executor
		int coreCount = Runtime.getRuntime().availableProcessors();
		System.out.println("Avvio una cached thread pool con dimensione massima " + coreCount*2);
		poolExecutor = new ThreadPoolExecutor(0, coreCount*2, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

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

					// Canale pronto per la lettura
					else if (key.isReadable()) {

						System.out.println("Canale pronto per la lettura!");
						SocketChannel client = (SocketChannel) key.channel();
						SocketAddress clientAddress = client.getRemoteAddress();
						TcpClientRequestDTO clientMessage = ServerMain.readTcpMessage(client);

						if (clientMessage == null) {
							System.out.println("Disconnessione forzata del client " + clientAddress);
							client.close();
							break;
						}

						System.out.println("Nuovo messaggio da client " + clientAddress + ":" + clientMessage);
						SelectionKey writeKey = client.register(selector, SelectionKey.OP_WRITE);
						// Faccio gestire la richiesta del client dal pool di thread
						try {
							poolExecutor.submit(new RequestTask(writeKey, clientMessage));
						} catch (RejectedExecutionException e) {
							System.out.println("Impossibile gestire nuova richiesta, thread pool rejection: "+e.getMessage());
							writeKey.attach(new TcpServerResponseDTO(INTERNAL_SERVER_ERROR));
						}
					}

					// Canale pronto per la scrittura. Il canale potrebbe essere pronto ma il thread potrebbe non aver
					// ancora messo nell'attachment la risposta da inviare al client
					else if (key.isWritable() && key.attachment() != null) {

						System.out.println("Canale pronto per la scrittura!");

						SocketChannel client = (SocketChannel) key.channel();
						TcpServerResponseDTO response = (TcpServerResponseDTO) key.attachment();

						if (response != null) {
							sendTcpMessage(client, response);
							// Registro nuovamente il client per un operazione di lettura
							client.register(selector, SelectionKey.OP_READ, null);
						}
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

	/**
	 * Funzione RMI per registrare un nuovo utente
	 * @param username
	 * @param password
	 * @throws RemoteException
	 * @throws IllegalArgumentException
	 */
	@Override
	public synchronized void register(String username, String password) throws RemoteException, IllegalArgumentException {

		// Controllo parametri
		if (username.isEmpty()) {
			throw new IllegalArgumentException(USERNAME_REQUIRED.name());
		}

		if (password.isEmpty()) {
			throw new IllegalArgumentException(PASSWORD_REQUIRED.name());
		}

		// Aggiungo nuovo utente al sistema
		try {
			User user = new User(username, password);
			this.userService.addUser(user);
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
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
	public static void notifyRankToClient(List<UserScore> rank) {
		for(Map.Entry<String, NotifyEventInterface> client: clients.entrySet()) {
			try {
				client.getValue().notifyUsersRank(rank);
			} catch (RemoteException e) {
				System.out.println("Errore durante invio aggiornamento classifica a utente "+client.getKey()+". " + e.getMessage());
			}
		}
	}
}