package server.exceptions;

import common.enums.ResponseCodeEnum;

/**
 * Custom exception with a
 */
public class WordleException extends Exception {

	private final ResponseCodeEnum error;
	public WordleException(ResponseCodeEnum error) {
		super(error.toString());
		this.error = error;
	}
}
