package common.utils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class WordleLogger {

	private static final String ANSI_RESET = "\u001B[0m";
	public static final String WHITE_BOLD = "\033[1;37m";
	public static final String CYAN_BOLD = "\033[1;36m";
	public static final String BLUE_BOLD = "\033[1;34m";
	public static final String RED_BOLD = "\033[1;31m";
	public static final String GREEN_BOLD = "\033[1;32m";
	public static final String YELLOW_BOLD = "\033[1;33m";

	private String className;
	public WordleLogger(String className) {
		String[] split = className.split("\\.");
		this.className = split[split.length-1];
	}

	private void log(String color, String level, String message) {
		//SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		System.out.println(
				"["+color + level + ANSI_RESET + "]" +
				"["+ WHITE_BOLD + Thread.currentThread().getName() + ANSI_RESET + "]" +
				"["+ CYAN_BOLD + className + ANSI_RESET + "]" +
				"["+ WHITE_BOLD + dateFormat.format(new Date()) + ANSI_RESET + "] " +
				message + "\n");
	}

	public void info(String message) {
		this.log(WHITE_BOLD, "INFO", message);
	}

	public void debug(String message) {
		if (System.getenv("WORDLE_DEBUG") != null) {
			this.log(BLUE_BOLD,"DEBUG", message);
		}
	}

	public void error(String message) {
		this.log(RED_BOLD,"ERROR", message);
	}

	public void warn(String message) {
		this.log(YELLOW_BOLD,"WARNING", message);
	}

	public void success(String message) {
		this.log(GREEN_BOLD, "SUCCESS", message);
	}
}
