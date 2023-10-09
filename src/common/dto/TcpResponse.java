package common.dto;

import common.enums.ResponseCodeEnum;

public class TcpResponse {
	public ResponseCodeEnum code; // Codice di risposta
	public LetterDTO[][] userGuess;
	public int remainingAttempts;
	public UserStat stat;
	public String wordTranslation;

	public TcpResponse(){
		this.code = ResponseCodeEnum.OK;
	}

	public TcpResponse(ResponseCodeEnum code) {
		this.code = code;
	}
}
