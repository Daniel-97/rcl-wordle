package common.dto;

public class LetterDTO {

	public char letter;
	public char guessStatus;

	@Override
	public String toString() {
		return "LetterDTO{" +
				"letter=" + letter +
				", guessStatus=" + guessStatus +
				'}';
	}
}
