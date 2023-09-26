package common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.dto.TcpMessageDTO;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class SocketUtils {

	private static final int BUFFER_SIZE = 1024;
	private final static Gson gson = new GsonBuilder().create();

	/**
	 * Manda il messaggio sul socket specificato
	 * @param socket
	 * @param message
	 * @throws IOException
	 */
	public static void sendTcpRequest(SocketChannel socket, TcpMessageDTO request) throws IOException {

		String json = gson.toJson(request);
		ByteBuffer command = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
		socket.write(command);
	}

	/**
	 * Legge un messaggio dal socket specificato
	 * @param socket
	 * @return
	 * @throws IOException
	 */
	public static TcpMessageDTO readTcpResponse(SocketChannel socket) throws IOException {

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

		return gson.fromJson(json.toString(), TcpMessageDTO.class);
	}
}
