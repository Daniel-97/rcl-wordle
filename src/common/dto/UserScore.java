package common.dto;

import java.io.Serializable;

public class UserScore implements Serializable {

	public String username;
	public int score;

	public UserScore(String username, int score){
		this.username = username;
		this.score = score;
	}

	public int getScore() {
		return score;
	}
}
