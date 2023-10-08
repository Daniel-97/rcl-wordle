package server.entity;

import java.util.Date;

public class WordleGameState {

	public String actualWord; // Parola attuale del gioco
	public Date extractedAt; // Indica quando e' stata estratta l'ultima parola
	public int gameNumber;

	public WordleGameState() {
		this.actualWord = null;
		this.extractedAt = null;
		this.gameNumber = 0;
	}
}
