package common.dto;

public class TcpServerResponseDTO {
	public boolean success;
	private String message;

	public TcpServerResponseDTO(boolean success, String message) {
		this.success = success;
		this.message = message;
	}
}
