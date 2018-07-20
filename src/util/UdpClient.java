package util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLContext;
import com.wolfssl.WolfSSLSession;

import server.DTLSGenCookieCallback;
import server.DTLSIOContext;
import server.DTLSRecvCallback;
import server.DTLSSendCallback;
import server.Envelope;

public class UdpClient {

	public static void main(String[] args) throws Exception {
		
		
		// // set socket and dtls config
		WolfSSL.loadLibrary();
		WolfSSL sslLib = new WolfSSL();
		sslLib.debuggingON();
		WolfSSLContext sslCtx = new WolfSSLContext(WolfSSL.DTLSv1_2_ClientMethod());
		/* load certificate/key files */
		int ret = sslCtx.useCertificateFile("crypto/client.pem", WolfSSL.SSL_FILETYPE_PEM);
		if (ret != WolfSSL.SSL_SUCCESS) {
			System.out.println("failed to load server certificate!");
			System.exit(1);
		}

		ret = sslCtx.usePrivateKeyFile("crypto/client.key", WolfSSL.SSL_FILETYPE_PEM);
		if (ret != WolfSSL.SSL_SUCCESS) {
			System.out.println("failed to load server private key!");
			System.exit(1);
		}
//		
		ret = sslCtx.loadVerifyLocations("crypto/trusted-ca/ca-server.pem", null);
        if (ret != WolfSSL.SSL_SUCCESS) {
            System.out.println("failed to load CA certificates!");
            System.exit(1);
        }
		// do not verify certificates with a CA
		sslCtx.setVerify(WolfSSL.SSL_VERIFY_PEER, null);

		// set dtls callbacks
		sslCtx.setIORecv(new DTLSRecvCallback());
		sslCtx.setIOSend(new DTLSSendCallback());
		System.out.println("Registered I/O callbacks");

		// register DTLS cookie generation callback
		sslCtx.setGenCookie(new DTLSGenCookieCallback());
		System.out.println("Registered DTLS cookie callback");

        /* create SSL object */
		WolfSSLSession ssl = new WolfSSLSession(sslCtx);
		
		DatagramSocket socket = new DatagramSocket();
		InetAddress ip = InetAddress.getByName("127.0.0.1");
        InetSocketAddress addr = new InetSocketAddress(ip, 5556);
        ret = ssl.dtlsSetPeer(addr);
        if (ret != WolfSSL.SSL_SUCCESS) {
            System.out.println("failed to set DTLS peer");
            System.exit(1);
        }
        DataInputStream is = null;
        DataOutputStream os = null;
        
		// setup io context
		DTLSIOContext ioctx = new DTLSIOContext(os, is, socket,ip, 5556);
		ssl.setIOReadCtx(ioctx);
		ssl.setIOWriteCtx(ioctx);
		System.out.println("Registered I/O callback user ctx");


        /* call wolfSSL_connect */
        ret = ssl.connect();
        if (ret != WolfSSL.SSL_SUCCESS) {
            int err = ssl.getError(ret);
            String errString = sslLib.getErrorString(err);
            System.out.println("wolfSSL_connect failed. err = " + err +
                    ", " + errString);
            System.exit(1);
        }
        
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream("crypto/client.pem");
        X509Certificate cer = (X509Certificate) fact.generateCertificate(fis);
        PublicKey key = cer.getPublicKey();

		Envelope env = new Envelope(Envelope.OP_GET_CONTRACT, "mainchannel", "xcc",key.getEncoded());
		byte[] envbytes = Envelope.toBytes(env);
        /* test write(long, byte[], int) */
        ret = ssl.write(envbytes, envbytes.length);

        byte[] back = new byte[1500];
        int input = ssl.read(back, back.length);
        if (input > 0) {
            System.out.println("got back a rsp envelope");
            Envelope retEnv = Envelope.fromBytes(back);
            System.out.println(retEnv.getChannelId());
            System.out.println(retEnv.getContractId());
            System.out.println(new String(retEnv.getPayload()));
        } else {
            System.out.println("read failed");
        }

	}
	


}
