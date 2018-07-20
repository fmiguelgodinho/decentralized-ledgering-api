package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DTLSIOContext {
	private DataOutputStream out;
	private DataInputStream in;
	private DatagramSocket dsock;
	private InetAddress hostAddress;
	private int port;

	public DTLSIOContext(DataOutputStream outStr, DataInputStream inStr, DatagramSocket s, InetAddress hostAddr, int port) {
		this.out = outStr;
		this.in = inStr;
		this.dsock = s;
		this.hostAddress = hostAddr;
		this.port = port;
	}

	public DataOutputStream getOutputStream() {
		return this.out;
	}

	public DataInputStream getInputStream() {
		return this.in;
	}

	public DatagramSocket getDatagramSocket() {
		return this.dsock;
	}

	public InetAddress getHostAddress() {
		return this.hostAddress;
	}

	public void setAddress(InetAddress addr) {
		this.hostAddress = addr;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}
}
