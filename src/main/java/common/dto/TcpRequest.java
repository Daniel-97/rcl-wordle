package common.dto;

import common.enums.TCPCommandEnum;

import java.util.Arrays;

public class TcpRequest {

	public final TCPCommandEnum command;
	public final String username;
	public final String data;

	public TcpRequest(TCPCommandEnum command, String username) {
		this.command = command;
		this.username = username;
		this.data = null;
	}

	public TcpRequest(TCPCommandEnum command, String username, String data) {
		this.command = command;
		this.username = username;
		this.data = data;
	}

	@Override
	public String toString() {
		return "TcpRequestDTO{" +
				"command='" + command + '\'' +
				", data=" + data +
				'}';
	}
}
