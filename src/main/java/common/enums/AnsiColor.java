package common.enums;

public class AnsiColor {
	public static final String RESET = "\033[0m";

	// Classic
	public static final String BLACK = "\033[0;30m";
	public static final String BLACK_BOLD = "\033[1;30m";
	public static final String GREEN = "\033[0;32m";
	public static final String YELLOW = "\033[0;33m";

	// Bold
	public static final String WHITE_BOLD = "\033[1;37m";
	public static final String CYAN_BOLD = "\033[1;36m";
	public static final String BLUE_BOLD = "\033[1;34m";
	public static final String RED_BOLD = "\033[1;31m";
	public static final String GREEN_BOLD = "\033[1;32m";
	public static final String YELLOW_BOLD = "\033[1;33m";

	// Background
	public static final String YELLOW_BACKGROUND = "\033[43m";
	public static final String GRAY_BACKGROUND = "\033[100m";
	public static final String GREEN_BACKGROUND = "\033[42m";
	public static final String RED_BACKGROUND = "\033[41m";

}
