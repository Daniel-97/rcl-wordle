package client;

import client.enums.ClientMode;
import client.enums.GuestCommand;
import client.enums.UserCommand;
import client.services.CLIHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.dto.LetterDTO;
import common.dto.TcpClientRequestDTO;
import common.dto.TcpServerResponseDTO;
import common.dto.UserStat;
import common.enums.ResponseCodeEnum;
import server.exceptions.WordleException;
import server.interfaces.ServerRMI;
import common.utils.ConfigReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Properties;

public class ClientMain {

	private final static String STUB_NAME = "WORDLE-SERVER";
	private final static int BUFFER_SIZE = 1024;
	private final static Gson gson = new GsonBuilder().create();
	private final int tcpPort;
	private final int rmiPort;
	private final String serverIP;
	private ServerRMI serverRMI;
	private SocketChannel socket;
	private String username = null;
	private ClientMode mode = ClientMode.GUEST_MODE;
	private boolean canPlayWord = false;


	public static void main(String[] argv) {

		if (argv == null || argv.length == 0) {
			System.out.println("Fornisci il path del file di configurazione come argomento!");
			System.exit(-1);
		}
		ClientMain client = new ClientMain(argv[0]);

		System.out.println("Tenativo di connessione con il server "+client.serverIP+":"+client.tcpPort);
		try {
			client.socket = SocketChannel.open();
			client.socket.connect(new InetSocketAddress(client.serverIP, client.tcpPort));
		} catch (IOException e) {
			System.out.println("Errore durante connessione tcp al server " + client.serverIP + ":"+client.tcpPort);
			System.exit(-1);
		}

		// Thread in ascolto di SIGINT e SIGTERM
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown Wordle client...");
				//socketChannel.close();
			}
		});

		System.out.println("Connesso con il server "+client.serverIP+":"+client.tcpPort);

		client.run();
	}

	public void run() {

		while (true) {

			switch (this.mode) {

				case GUEST_MODE:
					this.guestMode();
					break;

				case USER_MODE:
					this.userMode();
					break;
			}

		}
	}

	private void guestMode() {

		CLIHelper.entryMenu();
		String[] input = CLIHelper.parseInput();

		GuestCommand cmd = GuestCommand.fromCommand(input[0]);
		if (cmd == null) {
			System.out.println("Comando non valido!");
			return;
		}

		// Eseguo un comando
		switch (cmd) {
			case HELP:
				CLIHelper.entryMenu();
				break;

			case QUIT:
				try {
					this.socket.close();
				} catch (IOException e) {
					System.out.println("Errore chiusura socket con server");
				} finally {
					System.exit(0);
				}
				break;

			case LOGIN:
				if (input.length < 3) {
					System.out.println("Comando non valido!");
				} else {
					this.login(input[1], input[2]);
				}
				break;

			case REGISTER:
				if (input.length < 3) {
					System.out.println("Comando non completo");
				} else {
					this.register(input[1], input[2]);
				}
				break;
		}

		//CLIHelper.cls();
	}

	private void userMode() {

		CLIHelper.mainMenu();
		String[] input = CLIHelper.parseInput();

		UserCommand cmd = UserCommand.fromCommand(input[0]);
		if (cmd == null) {
			System.out.println("Invalid command!");
			return;
		}

		switch (cmd) {
			case HELP: {
				CLIHelper.mainMenu();
				break;
			}

			case LOGOUT: {
				this.logout(this.username);
				break;
			}

			case PLAY: {
				this.playWORDLE();
				break;
			}

			case SEND_WORD:
				if (input.length < 2) {
					System.out.println("Comando non valido!");
				} else {
					this.sendWord(input[1]);
				}

				break;

			case STAT:
				this.sendMeStatistics();
				break;

			case SHARE:
				break;

			default:
				System.out.println("Comando sconosciuto");

		}
	}

	private void sendWord(String word) {

		if (!this.canPlayWord) {
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
				System.out.println("Hai indovinato la parola!");
				this.canPlayWord = false;
			} else if(response.code == ResponseCodeEnum.INVALID_WORD_LENGHT) {
				System.out.println("Parola troppo lunga o troppo corta, tentativo non valido");
			} else if(response.code == ResponseCodeEnum.WORD_NOT_IN_DICTIONARY) {
				System.out.println("Parola non presente nel dizionario, tentativo non valido");
			} else if(response.code == ResponseCodeEnum.GAME_LOST) {
				System.out.println("Tentativi esauriti!");
				this.canPlayWord = false;
			} else {
				CLIHelper.printServerWord(response.userGuess);
			}
		}

	}

	private void login(String username, String password) {

		TcpClientRequestDTO requestDTO = new TcpClientRequestDTO("login", new String[]{username, password});

		try {
			sendTcpMessage(requestDTO);

			TcpServerResponseDTO response = readTcpMessage();
			if (response.success) {
				System.out.println("Login completato con successo");
				this.mode = ClientMode.USER_MODE;
				this.username = username;
			} else {
				System.out.println("Nome utente o password errati!");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante il login!");
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

	private void playWORDLE() {
		TcpClientRequestDTO request = new TcpClientRequestDTO("playWORDLE", new String[]{username});
		try {
			sendTcpMessage(request);

			TcpServerResponseDTO response = readTcpMessage();
			if (response.success) {
				System.out.println("Ok, puoi giocare a Wordle!");
				this.canPlayWord = true;
			} else {
				System.out.println("Errore, non puoi giocare con parola attuale");
			}
		} catch (IOException e) {
			System.out.println("Errore imprevisto durante richiesta di playWORDLE");
		}
	}

	private void register(String username, String password) {

		try {
			this.serverRMI.register(username, password);
		} catch (RemoteException e) {
			// TODO gestire il caso in cui il server si disconnette
			throw new RuntimeException(e);
		} catch (WordleException e) {
			System.out.println("Errore! " + e.getMessage());
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
				System.out.println("Statistiche mancanti!");
			}
		} catch (IOException e) {
			System.out.println("Errore richiesta statistiche");
		}
	}

	public ClientMain(String configPath) {

		System.out.println("Avvio Wordle game client...");

		// Leggi le configurazioni dal file
		Properties properties = ConfigReader.readConfig(configPath);
		this.tcpPort = Integer.parseInt(properties.getProperty("app.tcp.port"));
		this.rmiPort = Integer.parseInt(properties.getProperty("app.rmi.port"));
		this.serverIP = properties.getProperty("app.tcp.ip");

		// Inizializza RMI
		Remote RemoteObject = null;
		try {
			// TODO capire come specificare indirizzo ip remoto del server
			Registry registry = LocateRegistry.getRegistry(this.rmiPort);
			RemoteObject = registry.lookup(STUB_NAME);
			this.serverRMI = (ServerRMI) RemoteObject;

		} catch (RemoteException e) {
			System.out.println("Client remote exception: " + e.getMessage());
			System.exit(-1);
		} catch (NotBoundException e) {
			System.out.println("Client not bound exception" + e.getMessage());
			System.exit(-1);
		}

	}

	public void sendTcpMessage(TcpClientRequestDTO request) throws IOException {
		String json = gson.toJson(request);
		ByteBuffer command = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		this.socket.write(command);
	}

	public TcpServerResponseDTO readTcpMessage() throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		StringBuilder json = new StringBuilder();
		int bytesRead = 0;

		while ((bytesRead = this.socket.read(buffer)) > 0) {
			// Sposto il buffer il lettura
			buffer.flip();
			// Leggo i dati dal buffer
			json.append(StandardCharsets.UTF_8.decode(buffer));
			// Pulisco il buffer
			buffer.clear();
			// Sposto il buffer in scrittura
			buffer.flip();
		}

		return gson.fromJson(json.toString(), TcpServerResponseDTO.class);
	}
}
