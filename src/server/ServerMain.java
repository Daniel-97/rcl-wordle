package server;

import server.entity.User;
import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;
import server.interfaces.NotifyEvent;
import server.interfaces.ServerRMI;
import server.services.UserService;
import server.services.WordleGameService;
import common.utils.ConfigReader;
import common.utils.SocketUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

public class ServerMain extends RemoteObject implements ServerRMI {

	private final static String STUB_NAME = "WORDLE-SERVER";
	private final int tcpPort;
	private final int rmiPort;
	// Services
	private final UserService userService;
	private final WordleGameService wordleGameService;

	public static void main(String[] argv) {

		// Inizializza il server
		ServerMain server = new ServerMain(null); //TODO prendere path file di configurazione dagli argomenti

		// Thread in ascolto di SIGINT e SIGTERM
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown Wordle server...");
				server.userService.saveUsers();
				server.wordleGameService.saveState();
			}
		});

		server.listen();
	}

	public ServerMain(String configPath) {

		System.out.println("Avvio Wordle game server...");

		//TODO implementare wrapper per leggere le configurazione da un file data un interfaccia
		if (configPath == null || configPath.isEmpty()) {
			System.out.println("Nessun file di configurazione trovato, uso file di configurazione di default");
			configPath = "./src/server/app.config";
		}

		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig(configPath);
		this.tcpPort = Integer.parseInt(properties.getProperty("app.tcp.port"));
		this.rmiPort = Integer.parseInt(properties.getProperty("app.rmi.port"));

		// Inizializzo i servizi
		this.userService = new UserService();
		this.wordleGameService = new WordleGameService();

		// Inizializza RMI server
		try {
			// Esportazione oggetto
			ServerRMI stub = (ServerRMI) UnicastRemoteObject.exportObject(this, 0);
			// Creazione registry
			LocateRegistry.createRegistry(this.rmiPort);
			Registry registry = LocateRegistry.getRegistry(this.rmiPort);
			// Pubblicazione dello stub nel registry
			registry.bind(ServerMain.STUB_NAME, stub);

			System.out.println("RMI server in ascolto sulla porta " + this.rmiPort);
		} catch (AlreadyBoundException e){
			System.out.println("RMI already bind exception: " + e.getMessage());
			System.exit(-1);
		} catch (RemoteException e){
			System.out.println("RMI remote exception: " + e.getMessage());
			System.exit(-1);
		}

		// Inizializza TCP server

	}

	public void listen() {

		ServerSocket socket = null;
		ServerSocketChannel socketChannel = null;
		Selector selector = null;

		//todo Spostare questa parte nel costruttore
		try {

			//Creo il channel
			socketChannel = ServerSocketChannel.open();
			socket = socketChannel.socket();
			socket.bind(new InetSocketAddress(this.tcpPort));
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
				selector.select(); // Bloccate, si ferma fino a quando almeno un canale non e' pronto
			} catch (IOException e) {
				System.out.println("Errore durante la selezione di un canale");
				break;
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
						String clientMessage = SocketUtils.readResponse(client);

						System.out.println("Nuovo messaggio da client: "+ clientMessage);
						switch (clientMessage.toString()) {
							case "":
								System.out.println("Disconnessione forzata del client");
								client.close();
								//TODO utente disconnesso
								break;
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

		System.out.println("Calling register RMI...");
		// Controllo parametri
		if (username.isEmpty()) {
			throw new IllegalArgumentException(ErrorCodeEnum.USERNAME_REQUIRED.name());
		}

		if (password.isEmpty()) {
			throw new IllegalArgumentException(ErrorCodeEnum.PASSWORD_REQUIRED.name());
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
}