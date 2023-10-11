package common.dto;

public class UserStat {
	public int playedGames; // Partite giocate
	public int wonGamesPercentage; // Percentuale di partite vinte rispetto a quelle giocate
	public float avgAttemptsWonGames; // Media tentativi fatti per partite vinte
	public int lastStreakWonGames; // Ultima serie di partite vinte
	public int bestStreakWonGames; // Migliore serie di partite vinte
	public GuessDistributionItem[] guessDistribution; // Distribuzione tentativi su partite vinte

	public UserStat(){
		playedGames = 0;
		wonGamesPercentage = 0;
		avgAttemptsWonGames = 0;
		lastStreakWonGames = 0;
		bestStreakWonGames = 0;
		guessDistribution = null;
	}

}
