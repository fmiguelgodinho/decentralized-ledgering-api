package core.dto;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import com.google.protobuf.ByteString;

public class ChaincodeResult {
	
	public static final int CHAINCODE_FAILURE = 0;
	public static final int CHAINCODE_SUCCESS = 1;
	
	private int status;
	private String content;
	private Date timestamp;
	private List<ByteString> signatures;
	
	// for transaction invocation, where endorsement signatures and time might be interesting, but not content
	public ChaincodeResult(int status, Date timestamp, List<ByteString> signatures) {
		this.status = status;
		this.content = null;
		this.timestamp = timestamp;
		this.signatures = signatures;
	}
	
	// mostly for querying where we want to see results
	public ChaincodeResult(int status, String content, List<ByteString> signatures) {
		this.status = status;
		this.content = content;
		this.timestamp = null;
		this.signatures = signatures;
	}

	// mostly for failures or when content isnt needed
	public ChaincodeResult(int status) {
		this.status = status;
		this.content = null;
		this.timestamp = null;
		this.signatures = null;
	}

	public int getStatus() {
		return status;
	}
	
	public String getContent() {
		return content;
	}

	public List<String> getSignatures() {
    	List<String> sigResult = new ArrayList<String>();
    	for (ByteString sig : signatures) {
    		byte[] b64sig = Base64.getEncoder().encode(sig.toByteArray());
    		sigResult.add(new String(b64sig));
    	}
		return sigResult;
	}
	
	public Date getTimestamp() {
		return timestamp;
	}



}
