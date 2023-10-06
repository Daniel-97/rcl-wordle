package common.dto;

import common.enums.ResponseCodeEnum;

public class TcpServerResponseDTO {
	public ResponseCodeEnum code; //Codice di risposta
	public LetterDTO[][] userGuess;
	public int remainingAttempts;

	public UserStat stat;
	public String wordTranslation;

	public TcpServerResponseDTO(){
		this.code = ResponseCodeEnum.OK;
	}

	public TcpServerResponseDTO(ResponseCodeEnum code) {
		this.code = code;
	}

	public TcpServerResponseDTO(LetterDTO[][] userGuess, int remainingAttempts) {
		this.code = ResponseCodeEnum.OK;
		this.userGuess = userGuess;
		this.remainingAttempts = remainingAttempts;
	}
}
