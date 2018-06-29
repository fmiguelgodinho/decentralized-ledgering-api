package util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class SigningUtil {

	public static void main(String[] args) throws Exception {
		
		if (args.length < 2) {
			System.out.println("Usage: SigningUtil <path to private key in pkcs8 format> <file to sign> <file to dump signature>");
			System.exit(1);
		}
		
		// get private key
    	File privKeyFile = new File(args[0]);
    	BufferedInputStream bis = new BufferedInputStream(new FileInputStream(privKeyFile));
    	byte[] privKeyBytes = new byte[(int)privKeyFile.length()];
    	bis.read(privKeyBytes);
    	bis.close();
    	
    	// get file to sign
    	File toSignFile = new File(args[1]);
    	bis = new BufferedInputStream(new FileInputStream(toSignFile));
    	byte[] toSignBytes = new byte[(int)toSignFile.length()];
    	bis.read(toSignBytes);
    	bis.close();
    	
    	// create key from key file
    	KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    	KeySpec ks = new PKCS8EncodedKeySpec(privKeyBytes);
    	RSAPrivateKey privKey = (RSAPrivateKey) keyFactory.generatePrivate(ks);
    	
    	Signature sig = Signature.getInstance("SHA256withRSA");
    	sig.initSign(privKey);
    	sig.update(new String(toSignBytes).trim().getBytes());
    	byte[] signature = sig.sign();
    	
    	String b64Sig = new String(Base64.getEncoder().encode(signature));
    	
    	BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(args[2]));
    	bos.write(b64Sig.getBytes("UTF-8"));
    	bos.flush();
    	bos.close();
    	
	}

}
