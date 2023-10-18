package common.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

import static common.enums.AnsiColor.*;

public class WordleLogger {

	private String className;
	public WordleLogger(String className) {
		String[] split = className.split("\\.");
		this.className = split[split.length-1];
	}

	private void log(String color, String level, String message) {
		//SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		System.out.println(
				"["+color + level + RESET + "]" +
				"["+ WHITE_BOLD + Thread.currentThread().getName() + RESET + "]" +
				"["+ CYAN_BOLD + className + RESET + "]" +
				"["+ WHITE_BOLD + dateFormat.format(new Date()) + RESET + "] " +
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
