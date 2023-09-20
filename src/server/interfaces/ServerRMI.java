package server.interfaces;

import server.exceptions.WordleException;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerRMI extends Remote {

	/**
	 * Registra un nuovo utente con le credenziali specificate
	 * @param username
	 * @param password
	 * @return 0 successfully subscribed
	 * @throws RemoteException
	 * @throws WordleException
	 */
	int register(String username, String password) throws RemoteException, WordleException;

	/**
	 * Iscrive un particolare client agli eventi del server
	 * @param username
	 * @param event
	 * @throws RemoteException
	 */
	void subscribeClientToEvent(String username, NotifyEvent event) throws RemoteException;

	/**
	 * Disiscrive un particolare client dagli eventi del server
	 * @param username
	 * @param event
	 * @throws RemoteException
	 */
	void unsubscribeClientToEvent(String username, NotifyEvent event) throws RemoteException;


}
