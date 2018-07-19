package util;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import server.Envelope;

public class UdpClient {

	public static void main(String[] args) throws Exception {
		DatagramSocket socket = new DatagramSocket();
		InetAddress ip = InetAddress.getByName("localhost");
		Envelope env = new Envelope(Envelope.OP_GET_CONTRACT, "mainchannel", "xcc");
		byte[] envbytes = Envelope.toBytes(env);
		
		DatagramPacket sendPacket = new DatagramPacket(envbytes, envbytes.length, ip, 5556);
		socket.send(sendPacket);
		
		byte[] recvBuf = new byte[1500];
		DatagramPacket recvPacket = new DatagramPacket(recvBuf, 1500);
		socket.receive(recvPacket);
		
		String result = new String(recvPacket.getData());
		System.out.println(result);

	}

}
