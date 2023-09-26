package common.dto;

public class TcpRequestDTO {

	public final String command;
	public final String[] arguments;

	public TcpRequestDTO(String command, String[] arguments) {
		this.command = command;
		this.arguments = arguments;
	}
}
