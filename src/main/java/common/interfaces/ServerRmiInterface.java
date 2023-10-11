package common.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerRmiInterface extends Remote {

	/**
	 * Registra un nuovo utente con le credenziali specificate
	 * @param username
	 * @param password
	 * @return 0 successfully subscribed
	 * @throws RemoteException
	 * @throws IllegalArgumentException
	 */
	void register(String username, String password) throws RemoteException, IllegalArgumentException;

	/**
	 * Iscrive un particolare client agli eventi del server
	 * @param username
	 * @param event
	 * @throws RemoteException
	 */
	void subscribeClientToEvent(String username, NotifyEventInterface event) throws RemoteException;

	/**
	 * Disiscrive un particolare client dagli eventi del server
	 * @param username
	 * @throws RemoteException
	 */
	void unsubscribeClientFromEvent(String username) throws RemoteException;


}
