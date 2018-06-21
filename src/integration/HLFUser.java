package integration;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;

/**
 * <h1>AppUser</h1>
 * <p>
 * Basic implementation of the {@link User} interface.
 */
public class HLFUser implements User, Serializable {

	private static final long serializationId = 1L;
	private static final long serialVersionUID = -6287264119837208213L;

    private String name;
    private Set<String> roles;
    private String account;
    private String affiliation;
    private Enrollment enrollment;
    private String mspId;

    public HLFUser() {
        // no-arg constructor
    }

    public HLFUser(String name, String affiliation, String mspId, String certPath, String keyPath) {
        this.name = name;
        this.affiliation = affiliation;
        this.enrollment = getEnrollmentFromCertPath(certPath, keyPath);
        this.mspId = mspId;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    @Override
    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    @Override
    public String getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    @Override
    public Enrollment getEnrollment() {
        return enrollment;
    }

    public void setEnrollment(Enrollment enrollment) {
        this.enrollment = enrollment;
    }

    @Override
    public String getMspId() {
        return mspId;
    }

    public void setMspId(String mspId) {
        this.mspId = mspId;
    }

    @Override
    public String toString() {
        return "AppUser{" +
                "name='" + name + '\'' +
                "\n, roles=" + roles +
                "\n, account='" + account + '\'' +
                "\n, affiliation='" + affiliation + '\'' +
                "\n, enrollment=" + enrollment +
                "\n, mspId='" + mspId + '\'' +
                '}';
    }
    
    private Enrollment getEnrollmentFromCertPath(String certPath, String keyPath) {
	    return new Enrollment() {
	
	        @Override
	        public PrivateKey getKey() {
	            try {
	                return loadPrivateKey(Paths.get(keyPath));
	            } catch (Exception e) {
	                return null;
	            }
	        }
	
	        @Override
	        public String getCert() {
	            try {
	            	return new String(Files.readAllBytes(Paths.get(certPath)));
	            } catch (Exception e) {
	                return "";
	            }
	        }
	
	    };
    }
    
    private static PrivateKey loadPrivateKey(Path fileName) throws IOException, GeneralSecurityException {
        PrivateKey key = null;
        InputStream is = null;
        BufferedReader br  = null;
        try {
            is = new FileInputStream(fileName.toString());
             br = new BufferedReader(new InputStreamReader(is));
            StringBuilder builder = new StringBuilder();
            boolean inKey = false;
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if (!inKey) {
                    if (line.startsWith("-----BEGIN ") && line.endsWith(" PRIVATE KEY-----")) {
                        inKey = true;
                    }
                    continue;
                } else {
                    if (line.startsWith("-----END ") && line.endsWith(" PRIVATE KEY-----")) {
                        inKey = false;
                        break;
                    }
                    builder.append(line);
                }
            }
            byte[] encoded = DatatypeConverter.parseBase64Binary(builder.toString());
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
            key = kf.generatePrivate(keySpec);
        } finally {
            br.close();
            is.close();
        }
        return key;
    }
}
