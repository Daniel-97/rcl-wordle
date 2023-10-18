package client.worker;

import client.entity.ClientConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import common.entity.SharedGame;
import common.entity.WordleGame;
import common.utils.WordleLogger;
import server.services.JsonService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Worker che rimane in ascolto di nuovi messaggi sul gruppo di multicast
 */
public class MulticastWorker implements Runnable {
	private final MulticastSocket multicastSocket;
	private final List<SharedGame> userGames;
	private final WordleLogger logger = new WordleLogger(MulticastWorker.class.getName());

	public MulticastWorker(MulticastSocket ms, List<SharedGame> userGames) {

		this.multicastSocket = ms;
		this.userGames = userGames;
	}

	@Override
	public void run() {

		logger.debug("Multicast worker in ascolto...");
		final int BUFFER_SIZE = 8192;
		byte[] buffer = new byte[BUFFER_SIZE];
		DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

		while (!Thread.interrupted()) {

			try {
				this.multicastSocket.receive(dp);
				String json = new String(dp.getData(), 0, dp.getLength());

				try {
					SharedGame wordleGame = JsonService.fromJson(json, SharedGame.class);
					this.userGames.add(wordleGame);
				} catch (JsonSyntaxException e) {
					logger.error("Errore parsing gioco condiviso da altro utente: "+e.getMessage());
				}

			} catch (IOException e) {
				logger.error("Errore durante ricezione messaggio multicast!" + e.getMessage());
			}
		}

		try {
			logger.debug("Abbandono il gruppo di multicast...");
			multicastSocket.leaveGroup(InetAddress.getByName(ClientConfig.MULTICAST_IP));
		} catch (IOException e) {
			logger.error("Impossibile abbandonare gruppo di multicast! "+e.getMessage());
		}
	}
}
