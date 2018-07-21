package util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLContext;
import com.wolfssl.WolfSSLIORecvCallback;
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
		sslCtx.setIORecv(new DTLSClientRecvCallback());
		sslCtx.setIOSend(new DTLSSendCallback());
		System.out.println("Registered I/O callbacks");

		// register DTLS cookie generation callback
		sslCtx.setGenCookie(new DTLSGenCookieCallback());
		System.out.println("Registered DTLS cookie callback");

		/* create SSL object */
		WolfSSLSession ssl = new WolfSSLSession(sslCtx);

		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(0);
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
		DTLSIOContext ioctx = new DTLSIOContext(os, is, socket, ip, 5556);
		ssl.setIOReadCtx(ioctx);
		ssl.setIOWriteCtx(ioctx);
		System.out.println("Registered I/O callback user ctx");

		/* call wolfSSL_connect */
		ret = ssl.connect();
		if (ret != WolfSSL.SSL_SUCCESS) {
			int err = ssl.getError(ret);
			String errString = sslLib.getErrorString(err);
			System.out.println("wolfSSL_connect failed. err = " + err + ", " + errString);
			System.exit(1);
		}

		CertificateFactory fact = CertificateFactory.getInstance("X.509");
		FileInputStream fis = new FileInputStream("crypto/client.pem");
		X509Certificate cer = (X509Certificate) fact.generateCertificate(fis);
		PublicKey key = cer.getPublicKey();

		Envelope env = new Envelope(Envelope.OP_GET_CONTRACT, "mainchannel", "xcc", null, null, key.getEncoded());
		byte[] envbytes = Envelope.toBytes(env);
		/* test write(long, byte[], int) */
		ret = ssl.write(envbytes, envbytes.length);

		byte[] back = new byte[3000];
		int input = ssl.read(back, back.length);
		if (input > 0) {
			System.out.println("got back a rsp envelope");
			Envelope retEnv = Envelope.fromBytes(back);
			System.out.println(retEnv.getChannelId());
			System.out.println(retEnv.getContractId());
			System.out.println(new String(retEnv.getPayload()));
			
			ObjectMapper om = new ObjectMapper();
			JsonNode respJson = om.readTree(retEnv.getPayload());
			byte[] toSignBytes = respJson.get("hash").asText().getBytes();
			
			// sign
			
			// get private key
	    	File privKeyFile = new File("test/client.key.pkcs8");
	    	BufferedInputStream bis = new BufferedInputStream(new FileInputStream(privKeyFile));
	    	byte[] privKeyBytes = new byte[(int)privKeyFile.length()];
	    	bis.read(privKeyBytes);
	    	bis.close();
	    	
	    	// create key from key file
	    	KeyFactory keyFactory = KeyFactory.getInstance("RSA");
	    	KeySpec ks = new PKCS8EncodedKeySpec(privKeyBytes);
	    	RSAPrivateKey privKey = (RSAPrivateKey) keyFactory.generatePrivate(ks);
	    	
	    	Signature sig = Signature.getInstance("SHA256withRSA");
	    	sig.initSign(privKey);
	    	sig.update(new String(toSignBytes).trim().getBytes());
	    	byte[] signature = sig.sign();
	    	
	    	byte[] b64Sig = Base64.getEncoder().encode(signature);
	    	
			env = new Envelope(Envelope.OP_SIGN_CONTRACT, "mainchannel", "xcc", null, b64Sig, key.getEncoded());
			envbytes = Envelope.toBytes(env);
			/* test write(long, byte[], int) */
			ret = ssl.write(envbytes, envbytes.length);
			back = new byte[3000];
			input = ssl.read(back, back.length);
			if (input > 0) {
				System.out.println("got back a rsp envelope");
				retEnv = Envelope.fromBytes(back);
				System.out.println(retEnv.getChannelId());
				System.out.println(retEnv.getContractId());
				System.out.println(new String(retEnv.getPayload()));
				

				
				om = new ObjectMapper();
				respJson = om.readTree(retEnv.getPayload());
				boolean result = respJson.get("result").asBoolean();
				if (result) {
					args = new String[] { "1238912748479897248", "{\"foo\": 123, \"bar\":\"test123\"}"};
					ArrayNode an = om.valueToTree(args);
					env = new Envelope(Envelope.OP_INVOKE, "mainchannel", "xcc", "put", om.writer().writeValueAsBytes(an), key.getEncoded());
					envbytes = Envelope.toBytes(env);
					/* test write(long, byte[], int) */
					ret = ssl.write(envbytes, envbytes.length);
					back = new byte[3000];
					input = ssl.read(back, back.length);
					if (input > 0) {
						System.out.println("got back a rsp envelope");
						retEnv = Envelope.fromBytes(back);
						System.out.println(retEnv.getChannelId());
						System.out.println(retEnv.getContractId());
						System.out.println(new String(retEnv.getPayload()));
						
						env = new Envelope(Envelope.OP_QUERY, "mainchannel", "xcc", "queryAll", null, key.getEncoded());
						envbytes = Envelope.toBytes(env);
						/* test write(long, byte[], int) */
						ret = ssl.write(envbytes, envbytes.length);
						back = new byte[3000];
						input = ssl.read(back, back.length);
						if (input > 0) {
							System.out.println("got back a rsp envelope");
							retEnv = Envelope.fromBytes(back);
							System.out.println(retEnv.getChannelId());
							System.out.println(retEnv.getContractId());
							System.out.println(new String(retEnv.getPayload()));
						}
					}
						
				}
			}
	    	
		}
	}
	
	static class DTLSClientRecvCallback implements WolfSSLIORecvCallback {
		public int receiveCallback(WolfSSLSession ssl, byte[] buf, int sz, Object ctx) {

			DTLSIOContext ioctx = (DTLSIOContext) ctx;

			int dtlsTimeout;
			DatagramSocket dsock;
			DatagramPacket recvPacket;

			try {
				dtlsTimeout = ssl.dtlsGetCurrentTimeout() * 20000;
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

}
