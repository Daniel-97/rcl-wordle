package common.dto;

import common.enums.TCPCommandEnum;

import java.util.Arrays;

public class TcpRequest {

	public final TCPCommandEnum command;
	public final String username;
	public final String[] arguments;

	public TcpRequest(TCPCommandEnum command, String username) {
		this.command = command;
		this.username = username;
		this.arguments = null;
	}

	public TcpRequest(TCPCommandEnum command, String username, String[] arguments) {
		this.command = command;
		this.username = username;
		this.arguments = arguments;
	}

	@Override
	public String toString() {
		return "TcpRequestDTO{" +
				"command='" + command + '\'' +
				", arguments=" + Arrays.toString(arguments) +
				'}';
	}
}
