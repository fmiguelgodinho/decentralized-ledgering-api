package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLIORecvCallback;
import com.wolfssl.WolfSSLSession;

public class DTLSRecvCallback implements WolfSSLIORecvCallback {
	public int receiveCallback(WolfSSLSession ssl, byte[] buf, int sz, Object ctx) {

		DTLSIOContext ioctx = (DTLSIOContext) ctx;

		int dtlsTimeout;
		DatagramSocket dsock;
		DatagramPacket recvPacket;

		try {
			dtlsTimeout = ssl.dtlsGetCurrentTimeout() * 1000;
			dsock = ioctx.getDatagramSocket();
			dsock.setSoTimeout(dtlsTimeout);
			recvPacket = new DatagramPacket(buf, sz);

			dsock.receive(recvPacket);

			ioctx.setAddress(recvPacket.getAddress());
			ioctx.setPort(recvPacket.getPort());

		} catch (SocketTimeoutException ste) {
			return WolfSSL.WOLFSSL_CBIO_ERR_TIMEOUT;
		} catch (SocketException se) {
			se.printStackTrace();
			return WolfSSL.WOLFSSL_CBIO_ERR_GENERAL;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return WolfSSL.WOLFSSL_CBIO_ERR_GENERAL;
		} catch (Exception e) {
			e.printStackTrace();
			return WolfSSL.WOLFSSL_CBIO_ERR_GENERAL;
		}

		return recvPacket.getLength();
	}

}
