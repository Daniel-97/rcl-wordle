package client.enums;

/**
 * Comandi utente non loggato
 */
public enum GuestCommand {

	LOGIN(":login"),
	REGISTER(":register"),
	HELP(":help"),
	QUIT(":quit");

	private final String command;

	GuestCommand(String command) {
		this.command = command;
	}

	public static GuestCommand fromCommand(String cmd) {
		for (GuestCommand gc: GuestCommand.values()) {
			if (gc.command.equals(cmd))
				return gc;
		}
		return null;
	}
}
