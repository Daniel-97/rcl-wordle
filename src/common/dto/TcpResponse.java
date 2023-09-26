package common.dto;

public class TcpResponse {
	public final boolean success;
	private final int code;

	public TcpResponse(boolean success, int code) {
		this.success = success;
		this.code = code;
	}
}
