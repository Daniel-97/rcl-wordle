package common.entity;

import common.dto.LetterDTO;

import java.util.List;

public class SharedGame {
	public String username;
	public int gameNumber;
	public LetterDTO[][] hints;

	public SharedGame(String username, int gameNumber, LetterDTO[][] hints) {
		this.username = username;
		this.gameNumber = gameNumber;
		this.hints = hints;
	}
}
