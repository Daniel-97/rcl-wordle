package server.tasks;

import common.dto.TcpClientRequestDTO;
import common.dto.TcpServerResponseDTO;
import common.enums.ResponseCodeEnum;
import server.ServerMain;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class CommandTask implements Runnable {
	private final SelectionKey key;
	public CommandTask(SelectionKey key) {
		this.key = key;
	}

	@Override
	public void run() {
		try {
			SocketChannel client = (SocketChannel) key.channel();
			TcpClientRequestDTO clientMessage = ServerMain.readTcpMessage(client);
			SocketAddress clientAddress = client.getRemoteAddress();
			//SelectionKey writeKey = client.register(key.selector(), SelectionKey.OP_WRITE);
			//System.out.println(clientMessage.toString());
			if (clientMessage == null) {
				System.out.println("Disconnessione forzata del client " + clientAddress);
				client.close();
				return;
			}

			switch (clientMessage.command) {

				case LOGIN: {
					// TODO controllare se gli argomenti ci sono o meno
					//boolean success = this.userService.login(clientMessage.arguments[0], clientMessage.arguments[1]);
					//key.attach(new TcpServerResponseDTO(ResponseCodeEnum.OK));
					ServerMain.sendTcpMessage(client, new TcpServerResponseDTO(ResponseCodeEnum.OK));
					break;
				}
			}
			System.out.println("Nuovo messaggio da client " + clientAddress + ":" + clientMessage);

			SelectionKey writeKey = client.register(key.selector(), SelectionKey.OP_READ);

		}catch (IOException e) {}

	}
}
