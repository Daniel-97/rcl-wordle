package common.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class SocketUtils {

	private static final int BUFFER_SIZE = 1024;

	/**
	 * Manda il messaggio sul socket specificato
	 * @param socket
	 * @param message
	 * @throws IOException
	 */
	public static void sendRequest(SocketChannel socket, String message) throws IOException {
		ByteBuffer command = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
		socket.write(command);
	}

	/**
	 * Legge un messaggio dal socket specificato
	 * @param socket
	 * @return
	 * @throws IOException
	 */
	public static String readResponse(SocketChannel socket) throws IOException {

		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		StringBuilder response = new StringBuilder();
		int bytesRead = 0;

		while ((bytesRead = socket.read(buffer)) > 0) {
			// Sposto il buffer il lettura
			buffer.flip();
			// Leggo i dati dal buffer
			response.append(StandardCharsets.UTF_8.decode(buffer));
			// Pulisco il buffer
			buffer.clear();
			// Sposto il buffer in scrittura
			buffer.flip();
		}

		return response.toString();
	}
}
