package server;

import java.net.InetAddress;;

public class DTLSGenCookieContext {
	private InetAddress hostAddr = null;
	private int port = 0;

	public DTLSGenCookieContext(InetAddress hostAddr, int port) {
		this.hostAddr = hostAddr;
		this.port = port;
	}

	public InetAddress getAddress() {
		return hostAddr;
	}

	public int getPort() {
		return port;
	}
}