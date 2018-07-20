package server;

import java.nio.ByteBuffer;

public class Envelope {

	public static final short ERR = -1;						// ENV: -1|n|channel|n|contract|0|n|payload(error)
	
	public static final short OP_GET_CONTRACT = 0;			// ENV: 0|n|channel|n|contract|0|0|n|pubkey
	public static final short OP_SIGN_CONTRACT = 1;			// ENV: 1|n|channel|n|contract|0|n|payload(signature)
	public static final short OP_QUERY = 2;					// ENV: 2|n|channel|n|contract|n|function|n|payload(args)
	public static final short OP_INVOKE = 3;				// ENV: 3|n|channel|n|contract|n|function|n|payload(args)
	public static final short RSP_GET_CONTRACT = 4;			// ENV: 4|n|channel|n|contract|0|n|payload(contract)
	public static final short RSP_SIGN_CONTRACT = 5;		// ENV: 5|n|channel|n|contract|0|n|payload(confirmation)
	public static final short RSP_QUERY = 6;				// ENV: 6|n|channel|n|contract|n|function|n|payload(result[query-result])
	public static final short RSP_INVOKE = 7;				// ENV: 7|n|channel|n|contract|n|function|n|payload(result[t/f], sigs)
	
	private short operation;
	private String channelId;
	private String contractId;
	private String function;
	private byte[] payload;
	
	private byte[] pubKey;									// public key that can be annexed to the envelope, usually set by a client app
	
	public Envelope(short operation, String channelId, String contractId, byte[] pubKey) {
		this(operation, channelId, contractId, null, null, pubKey);
	}
	
	public Envelope(short operation, String channelId, String contractId, byte[] payload, byte[] pubKey) {
		this(operation, channelId, contractId, null, payload, pubKey);
	}
	
	public Envelope(short operation, String channelId, String contractId, String function, byte[] pubKey) {
		this(operation, channelId, contractId, function, null, pubKey);
	}
	
	public Envelope(short operation, String channelId, String contractId, String function, byte[] payload, byte[] pubKey) throws IllegalArgumentException {
		
		if (operation < ERR || operation > RSP_INVOKE) {
			throw new IllegalArgumentException("Op code on envelope is not recognized.");
		}
		this.operation = operation;
		
		if (channelId == null) {
			throw new IllegalArgumentException("Channel ID cannot be null on envelope.");
		}
		this.channelId = channelId;
		
		if (contractId == null) {
			throw new IllegalArgumentException("Contract ID cannot be null on envelope.");
		}
		this.contractId = contractId;
		
		this.function = function;
		this.payload = payload;
		this.pubKey = pubKey;
	}
	
	public short getOpCode() {
		return operation;
	}
	
	public String getChannelId() {
		return channelId;
	}
	
	public String getContractId() {
		return contractId;
	}
	
	public String getFunction() {
		return function;
	}
	
	public byte[] getPayload() {
		return payload;
	}
	
	public byte[] getPublicKey() {
		return pubKey;
	}
	
	public void setPublicKey(byte[] pubKey) {
		this.pubKey = pubKey;
	}
	
	public static Envelope fromBytes(byte[] raw) {
		
		// get op code
		ByteBuffer wrapped = ByteBuffer.wrap(raw); // big-endian by default
		short operation = wrapped.getShort();
		
		// get channel id
		int chanidBufferSize = wrapped.getInt();
		byte[] chanidBuffer = new byte[chanidBufferSize];
		wrapped.get(chanidBuffer);
		String channelId = new String(chanidBuffer);
		
		// get contract id
		int cidBufferSize = wrapped.getInt();
		byte[] cidBuffer = new byte[cidBufferSize];
		wrapped.get(cidBuffer);
		String contractId = new String(cidBuffer);
		
		// get function id (if any)
		String function = null;
		int fnBufferSize = wrapped.getInt();
		if (fnBufferSize > 0) {
			byte[] fnBuffer = new byte[fnBufferSize];
			wrapped.get(fnBuffer);
			function = new String(fnBuffer);
		}
		
		// get payload / arguments (if any)
		byte[] payload = null;
		int payloadSize = wrapped.getInt();
		if (payloadSize > 0) {
			payload = new byte[payloadSize];
			wrapped.get(payload);
		}
		
		// finally get public key (if any)
		byte[] pubKey = null;
		int pubKeySize = wrapped.getInt();
		if (pubKeySize > 0) {
			pubKey = new byte[pubKeySize];
			wrapped.get(pubKey);
		}
		

		return new Envelope(operation, channelId, contractId, function, payload, pubKey);
	}
	
	public static byte[] toBytes(Envelope env) {

		// env parameters to be stored in byte array
		short operation = env.getOpCode();
		byte[] channelId = env.getChannelId().getBytes();
		byte[] contractId = env.getContractId().getBytes();
		byte[] function = null;					// this one we need to check after
		byte[] payload = env.getPayload();
		byte[] pubkey = env.getPublicKey();
		
		// check if this exists
		String functionStr = env.getFunction();
		if (functionStr != null) {
			function = functionStr.getBytes();
		}
		
		// allocate according to env parameters
		ByteBuffer buf = ByteBuffer.allocate(
				Short.BYTES + Integer.BYTES + channelId.length + Integer.BYTES + contractId.length
				+ (function != null? Integer.BYTES + function.length : Integer.BYTES)
				+ (payload != null? Integer.BYTES + payload.length : Integer.BYTES)
				+ (pubkey != null? Integer.BYTES + pubkey.length : Integer.BYTES)
		);
		
		// put into the buffer
		buf.putShort(operation);
		buf.putInt(channelId.length);
		buf.put(channelId);
		buf.putInt(contractId.length);
		buf.put(contractId);
		if (function != null) {
			buf.putInt(function.length);
			buf.put(function);
		} else {
			buf.putInt(0);
		}
		if (payload != null) {
			buf.putInt(payload.length);
			buf.put(payload);
		} else {
			buf.putInt(0);
		}
		if (pubkey != null) {
			buf.putInt(pubkey.length);
			buf.put(pubkey);
		} else {
			buf.putInt(0);
		}
		
		return buf.array();
	}
 
	
}
