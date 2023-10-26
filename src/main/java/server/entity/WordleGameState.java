package server.entity;

import java.util.Date;

public class WordleGameState {

	public String word; // Parola attuale del gioco
	public String translation; // Traduzione della parola
	public Date extractedAt; // Indica quando e' stata estratta l'ultima parola
	public int gameNumber;

	public WordleGameState() {
		this.word = null;
		this.translation = null;
		this.extractedAt = null;
		this.gameNumber = 0;
	}
}
