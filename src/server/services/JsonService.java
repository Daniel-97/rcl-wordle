package server.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonService {

	private final static Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public static Object readJson(String path, Type type) throws IOException {

		Path jsonPath = Paths.get(path);
		Object parsedObj = null;

		if (!Files.exists(jsonPath)) {
			throw new FileNotFoundException("File " + path + " not found!");
		}

		BufferedReader br = Files.newBufferedReader(jsonPath);
		parsedObj = gson.fromJson(br, type);

		System.out.println("File " + path + " letto correttamente!");
		return parsedObj;
	}

	/**
	 * Write object params to json
	 * @param path
	 * @param object
	 * @param <T>
	 * @throws IOException
	 */
	public static <T> void writeJson(String path, T object) throws IOException {

		Path jsonPath = Paths.get(path);

		// Se il file non esiste lo devo creare prima (incluse tutte le directory per arrivare al file)
		if (!Files.exists(jsonPath)) {
			Files.createDirectories(jsonPath.getParent());
			Files.createFile(jsonPath);
		}

		BufferedWriter bw =  Files.newBufferedWriter(jsonPath);
		String json = gson.toJson(object);
		bw.write(json);
		bw.close();

		int wroteBytes = json.getBytes().length;
		System.out.println("Salvataggio di " + path + " completato! (" + wroteBytes + " bytes)");
	}
}
