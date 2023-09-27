package common.dto;

import java.util.Arrays;

public class TcpClientRequestDTO {

	// Todo rendere questo comando un enumerato
	public final String command;
	public final String[] arguments;

	public TcpClientRequestDTO(String command, String[] arguments) {
		this.command = command;
		this.arguments = arguments;
	}

	public TcpClientRequestDTO(boolean success) {
		this.command = null;
		this.arguments = null;
	}

	@Override
	public String toString() {
		return "TcpRequestDTO{" +
				"command='" + command + '\'' +
				", arguments=" + Arrays.toString(arguments) +
				'}';
	}
}
