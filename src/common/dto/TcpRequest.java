package common.dto;

import common.enums.ServerTCPCommandEnum;

import java.util.Arrays;

public class TcpRequest {

	public final ServerTCPCommandEnum command;
	public final String[] arguments;

	public TcpRequest(ServerTCPCommandEnum command, String[] arguments) {
		this.command = command;
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
