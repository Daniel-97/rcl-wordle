package client.worker;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;

/**
 * Worker che rimane in ascolto di nuovi messaggi sul gruppo di multicast
 */
public class MulticastWorker implements Runnable {

	private final MulticastSocket multicastSocket;
	private final int BUFFER_SIZE = 2048;

	public MulticastWorker(MulticastSocket ms) {
		this.multicastSocket = ms;
	}

	@Override
	public void run() {

		System.out.println("Avvio multicast worker...");
		byte[] buffer = new byte[BUFFER_SIZE];
		DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

		while (!Thread.interrupted()) {

			try {
				this.multicastSocket.receive(dp);
				String message = dp.toString();
				System.out.println("Ricevuto nuovo messaggio multicast! "+message);

			} catch (IOException e) {
				System.out.println("Errore durante ricezione messaggio multicast!" + e.getMessage());
			}
		}
	}
}
