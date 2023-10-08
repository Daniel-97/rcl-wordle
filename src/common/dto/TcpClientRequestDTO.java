package common.dto;

import common.enums.ServerTCPCommand;

import java.util.Arrays;

public class TcpClientRequestDTO {

	public final ServerTCPCommand command;
	public final String[] arguments;

	public TcpClientRequestDTO(ServerTCPCommand command, String[] arguments) {
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
