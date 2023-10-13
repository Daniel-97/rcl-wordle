package common.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WordleLogger {

	private static final String ANSI_RESET = "\u001B[0m";
	private static final String ANSI_RED = "\u001B[31m";
	private static final String ANSI_GREEN = "\u001B[32m";
	private static final String ANSI_YELLOW = "\u001B[33m";
	private static final String ANSI_BLUE = "\u001B[34m";
	private static final String ANSI_WHITE = "\u001B[37m";
	private String className;
	public WordleLogger(String name) {
		this.className = name;
	}

	private void log(String color, String level, String message) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		System.out.println(
				"["+color + level + ANSI_RESET + "]" +
				"["+ ANSI_WHITE + Thread.currentThread().getName() + ANSI_RESET + "]" +
				"["+ ANSI_WHITE + className + ANSI_RESET + "]" +
				"["+ ANSI_WHITE + dateFormat.format(new Date()) + ANSI_RESET + "] " +
				message);
	}

	public void info(String message) {
		this.log(ANSI_WHITE, "INFO", message);
	}

	public void debug(String message) {
		if (System.getenv("WORDLE_DEBUG") != null) {
			this.log(ANSI_BLUE,"DEBUG", message);
		}
	}

	public void error(String message) {
		this.log(ANSI_RED,"ERROR", message);
	}

	public void warn(String message) {
		this.log(ANSI_YELLOW,"WARNING", message);
	}

	public void success(String message) {
		this.log(ANSI_GREEN, "SUCCESS", message);
	}
}
