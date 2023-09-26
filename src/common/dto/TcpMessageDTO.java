package common.dto;

public class TcpMessageDTO {

	public final String command;
	public final boolean success;
	private final int code;

	public TcpMessageDTO(String command) {
		this.command = command;
		this.success = false;
		this.code = 0;
	}

	public TcpMessageDTO(String command, boolean success, int code) {
		this.command = command;
		this.success = success;
		this.code = code;
	}

	@Override
	public String toString() {
		return "TcpMessageDTO{" +
				"command='" + command + '\'' +
				", success=" + success +
				", code=" + code +
				'}';
	}
}
