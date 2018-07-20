package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLIOSendCallback;
import com.wolfssl.WolfSSLSession;

public class DTLSSendCallback implements WolfSSLIOSendCallback {
	
	public int sendCallback(WolfSSLSession ssl, byte[] buf, int sz, Object ctx) {

		DTLSIOContext ioctx = (DTLSIOContext) ctx;

		DatagramSocket dsock = ioctx.getDatagramSocket();
		InetAddress hostAddr = ioctx.getHostAddress();
		int port = ioctx.getPort();
		DatagramPacket dp = new DatagramPacket(buf, sz, hostAddr, port);

		try {
			dsock.send(dp);

		} catch (IOException ioe) {
			ioe.printStackTrace();
			return WolfSSL.WOLFSSL_CBIO_ERR_GENERAL;
		} catch (Exception e) {
			e.printStackTrace();
			return WolfSSL.WOLFSSL_CBIO_ERR_GENERAL;
		}

		return dp.getLength();
	}
}
