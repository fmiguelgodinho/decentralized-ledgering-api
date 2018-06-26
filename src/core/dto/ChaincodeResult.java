package core.dto;

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
	
	// for transaction invokation, where endorsement signatures and time might be interesting, but not content
	public ChaincodeResult(int status, Date timestamp, List<ByteString> signatures) {
		this.status = status;
		this.content = null;
		this.timestamp = timestamp;
		this.signatures = signatures;
	}
	
	// mostly for querying where we just want to see results
	public ChaincodeResult(int status, String content) {
		this.status = status;
		this.content = content;
		this.timestamp = null;
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

	public Date getTimestamp() {
		return timestamp;
	}

	public List<ByteString> getSignatures() {
		return signatures;
	}



}
