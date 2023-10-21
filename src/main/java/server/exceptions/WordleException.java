package server.exceptions;

import common.enums.ResponseCodeEnum;

public class WordleException extends Exception {

	private final ResponseCodeEnum code;

	public WordleException(ResponseCodeEnum code) {
		super(code.name());
		this.code = code;
	}

	public ResponseCodeEnum getCode() {
		return code;
	}
}
