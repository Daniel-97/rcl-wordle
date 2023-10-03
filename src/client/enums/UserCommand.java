package client.enums;

/**
 * Comandi utente loggato
 */
public enum UserCommand {
	LOGIN(":login"),
	REGISTER(":register"),
	PLAY(":play"),
	SHARE(":share"),
	STAT(":stat"),
	RANK(":rank"),
	SHARING(":sharing"),
	LOGOUT(":logout"),
	HELP(":help"),
	QUIT(":quit");

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
