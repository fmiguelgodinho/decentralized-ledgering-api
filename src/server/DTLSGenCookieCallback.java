package server;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLGenCookieCallback;
import com.wolfssl.WolfSSLSession;

public class DTLSGenCookieCallback implements WolfSSLGenCookieCallback {
	public int genCookieCallback(WolfSSLSession ssl, byte[] buf, int sz, Object ctx) {

		int port = 0;
		byte[] out = null;
		InetAddress hostAddr = null;
		MessageDigest digest = null;

		DTLSGenCookieContext gctx = (DTLSGenCookieContext) ctx;
		hostAddr = gctx.getAddress();
		port = gctx.getPort();

		if ((hostAddr == null) || (port == 0))
			return WolfSSL.GEN_COOKIE_E;

		try {

			digest = MessageDigest.getInstance("SHA-1");
			digest.reset();
			digest.update(hostAddr.getHostAddress().getBytes());
			digest.update((byte) port);

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return WolfSSL.GEN_COOKIE_E;
		}

		out = new byte[digest.getDigestLength()];

		out = digest.digest();
		if (sz > digest.getDigestLength())
			sz = digest.getDigestLength();

		System.arraycopy(out, 0, buf, 0, sz);

		return buf.length;
	}
}
