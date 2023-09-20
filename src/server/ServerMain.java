package server;

import server.enums.ErrorCodeEnum;
import server.exceptions.WordleException;
import server.interfaces.NotifyEvent;
import server.interfaces.ServerRMI;
import server.services.UserService;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

public class ServerMain extends RemoteObject implements ServerRMI {

	private final UserService userService = new UserService();

	public static void main(String[] argv) {
		ServerMain server = new ServerMain();

	}


	@Override
	public int register(String username, String password) throws RemoteException, WordleException {

		// Controllo parametri
		if (username.isEmpty()) {
			throw new IllegalArgumentException(ErrorCodeEnum.USERNAME_REQUIRED.name());
		}

		if (password.isEmpty()) {
			throw new IllegalArgumentException(ErrorCodeEnum.PASSWORD_REQUIRED.name());
		}

		// Aggiungo nuovo utente al sistema
		User user = new User(username, password);
		this.userService.addUser(user);

		return 0;
	}

	@Override
	public void subscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}

	@Override
	public void unsubscribeClientToEvent(String username, NotifyEvent event) throws RemoteException {

	}
}