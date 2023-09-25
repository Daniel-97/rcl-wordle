package server.exceptions;

import server.enums.ErrorCodeEnum;

/**
 * Custom exception with a
 */
public class WordleException extends Exception {

	private final ErrorCodeEnum error;
	public WordleException(ErrorCodeEnum error) {
		super(error.toString());
		this.error = error;
	}
}
