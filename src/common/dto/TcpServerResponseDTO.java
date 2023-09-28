package common.dto;

import java.util.Arrays;

public class TcpServerResponseDTO {
	public boolean success;
	public String message;
	public LetterDTO[] word;
	public int remainingAttempts;

	public TcpServerResponseDTO(boolean success, String message) {
		this.success = success;
		this.message = message;
	}

	public TcpServerResponseDTO(LetterDTO[] word, int remainingAttempts) {
		this.word = word;
		this.remainingAttempts = remainingAttempts;
	}

	@Override
	public String toString() {
		return "TcpServerResponseDTO{" +
				"success=" + success +
				", message='" + message + '\'' +
				", word=" + Arrays.toString(word) +
				", remainingAttempts=" + remainingAttempts +
				'}';
	}
}
