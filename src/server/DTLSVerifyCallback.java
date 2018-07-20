package server;

import com.wolfssl.WolfSSL;
import com.wolfssl.WolfSSLVerifyCallback;

public class DTLSVerifyCallback implements WolfSSLVerifyCallback {

    public int verifyCallback(int preverify_ok, long x509StorePtr) {
        System.out.println("Hello from Java verify callback!");
        System.out.println(preverify_ok);
        System.out.println(x509StorePtr);
        

		byte[] der = WolfSSL.x509_getDer(x509StorePtr);
		
        
        return 1;
    }
}