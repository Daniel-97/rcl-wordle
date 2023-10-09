package common.dto;

public class UserStat {
	public int playedGames; // Partite giocate
	public int wonGamesPercentage; // Percentuale di partite vinte rispetto a quelle giocate
	public float avgAttemptsWonGames; // Media tentativi fatti per partite vinte
	public float standardDeviation; // Deviazione standard dei tentativi fatti per la vittoria
	public int lastStreakWonGames; // Ultima serie di partite vinte
	public int bestStreakWonGames; // Migliore serie di partite vinte

	public UserStat(){}

}
