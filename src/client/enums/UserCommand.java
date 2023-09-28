package client.enums;

/**
 * Comandi utente loggato
 */
public enum UserCommand {
	PLAY(":play"),
	SHARE(":share"),
	STAT(":stat"),
	LOGOUT(":logout"),
	SEND_WORD(":sendword"),
	HELP(":help");

	private final String command;
	UserCommand(String command) {
		this.command = command;
	}

	public static UserCommand fromCommand(String cmd) {
		for (UserCommand uc: UserCommand.values()) {
			if (uc.command.equals(cmd))
				return uc;
		}
		return null;
	}
}
