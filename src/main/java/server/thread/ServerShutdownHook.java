package server.thread;

import common.utils.WordleLogger;
import server.ServerMain;
import server.services.UserService;
import server.services.WordleGameService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Thread avviato alla ricezione di SIGINT o SIGTERM
 */
public class ServerShutdownHook extends Thread {

	private final WordleLogger logger = new WordleLogger(ServerShutdownHook.class.getName());
	private final UserService userService = UserService.getInstance();
	private final WordleGameService wordleGameService = WordleGameService.getInstance();

	@Override
	public void run() {
		logger.info("Terminazione Wordle server...");

		// Richiesta di terminazione graduale del thread pool
		ServerMain.poolExecutor.shutdown();
		try {
			// Attendo che la threadpool sia terminata per un massimo di 10 secondi
			if (ServerMain.poolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
				logger.debug("Thread pool terminata correttamente");
			}
		} catch (InterruptedException ignore) {}

		// Interrompo word update
		ServerMain.wordUpdateExecutor.shutdown();
		// Salvo utenti su file
		this.userService.saveUsers();
		// Salvo stato del gioco su file
		this.wordleGameService.saveState();
		// Chiudo socket multicast
		ServerMain.multicastSocket.close();
		// Chiudo socket channel
		try {ServerMain.socketChannel.close();} catch (IOException ignore) {}
	}
}
