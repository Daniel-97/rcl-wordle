package server;

import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;
import server.interfaces.NotifyEvent;
import server.interfaces.ServerRMI;
import server.services.UserService;
import utils.ConfigReader;

import java.net.BindException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

public class ServerMain extends RemoteObject implements ServerRMI {

	private final static String STUB_NAME = "WORDLE-SERVER";
	private final int tcpPort;
	private final int rmiPort;
	// Services
	private final UserService userService;

	public static void main(String[] argv) {

		// Inizializza il server
		ServerMain server = new ServerMain(null); //TODO prendere path file di configurazione dagli argomenti

		// Inizializza RMI
		try {
			// Esportazione oggetto
			ServerRMI stub = (ServerRMI) UnicastRemoteObject.exportObject(server, 0);
			// Creazione registry
			LocateRegistry.createRegistry(server.rmiPort);
			Registry registry = LocateRegistry.getRegistry(server.rmiPort);
			// Pubblicazione dello stub nel registry
			registry.bind(ServerMain.STUB_NAME, stub);

			System.out.println("RMI server in ascolto sulla porta " + server.rmiPort);
		} catch (AlreadyBoundException e){
			System.out.println("RMI already bind exception: " + e.getMessage());
		} catch (RemoteException e){
			System.out.println("RMI remote exception: " + e.getMessage());
		}

	}

	public ServerMain(String configPath) {

		System.out.println("Avvio Wordle game server...");

		if (configPath == null || configPath.isEmpty()) {
			System.out.println("Nessun file di configurazione trovato, uso file di configurazione di default");
			configPath = "./src/server/app.config";
		}

		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig(configPath);
		this.tcpPort = Integer.parseInt(properties.getProperty("app.tcp.port"));
		this.rmiPort = Integer.parseInt(properties.getProperty("app.rmi.port"));

		this.userService = new UserService();
	}

	@Override
	public int register(String username, String password) throws RemoteException, WordleException {

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

		return 0;
	}

	@Override
	public void subscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}

	@Override
	public void unsubscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}
}