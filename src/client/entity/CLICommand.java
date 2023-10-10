package client.entity;

import client.enums.UserCommandEnum;

public class CLICommand {
	public UserCommandEnum command;
	public String[] args;

	public CLICommand(UserCommandEnum command, String[] args) {
		this.command = command;
		this.args = args;
	}
}
