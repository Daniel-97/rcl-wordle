package server.entity;

import common.dto.TcpResponse;

public class SelectionKeyAttachment {
	public String username;
	public TcpResponse response;

	public SelectionKeyAttachment(TcpResponse response) {
		username = null;
		this.response = response;
	}
}
