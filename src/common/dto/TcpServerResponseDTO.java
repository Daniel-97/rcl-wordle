package common.dto;

import common.enums.ResponseCodeEnum;

public class TcpServerResponseDTO {
	public boolean success;
	public LetterDTO[][] userGuess;
	public int remainingAttempts;
	public ResponseCodeEnum code; //Codice di risposta
	public UserStat stat;

	public TcpServerResponseDTO(boolean success, ResponseCodeEnum code) {
		this.success = success;
		this.code = code;
	}

	public TcpServerResponseDTO(){}

	public TcpServerResponseDTO(LetterDTO[][] userGuess, int remainingAttempts) {
		this.success = true;
		this.userGuess = userGuess;
		this.remainingAttempts = remainingAttempts;
	}
}
