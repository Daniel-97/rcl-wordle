package common.dto;

import java.util.Arrays;

public class TcpMessageDTO {

	// Todo rendere questo comando un enumerato
	public final String command;
	public final String[] arguments;
	public final boolean success;
	private final int code;

	public TcpMessageDTO(String command, String[] arguments) {
		this.command = command;
		this.arguments = arguments;
		this.success = false;
		this.code = 0;
	}

	public TcpMessageDTO(String command, String[] arguments, boolean success, int code) {
		this.command = command;
		this.arguments = arguments;
		this.success = success;
		this.code = code;
	}

	@Override
	public String toString() {
		return "TcpMessageDTO{" +
				"command='" + command + '\'' +
				", arguments=" + Arrays.toString(arguments) +
				", success=" + success +
				", code=" + code +
				'}';
	}
}
