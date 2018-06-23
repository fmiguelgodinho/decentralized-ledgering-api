package core;

import org.apache.commons.configuration2.Configuration;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import core.dto.Contract;
import integration.Dispatcher;

public class ContractInterpreter {
	
//	private Map<String, Contract> contracts;	// list of contracts loaded in memory (CID -> contract)
												// if a contract isn't here, this interpreter will fetch it from the BC
	
	private DBCollection contractCollection;
	private Dispatcher dpt;
	
	public ContractInterpreter(Configuration cfg, MongoClient dbClient, Dispatcher dpt) {
//		contracts = new HashMap<String, Contract>();
		DB db = dbClient.getDB(cfg.getString("mongo.database"));
		contractCollection = db.getCollection(cfg.getString("mongo.contractCollection"));
		this.dpt = dpt;
	}
	
	public void saveRawContract(String channel, String cid, String rawContract) {
		
		// build obj being saved
		final DBObject contractObj = new BasicDBObject()
				.append("key", new BasicDBObject()
					.append("channelId", channel)
					.append("contractId", cid))
				.append("contract", rawContract);
		
		// save to db
		contractCollection.insert(contractObj);
	}
	
	public Contract getContract(String channel, String cid) {
		// encapsulate in Contract object
		String rawContract = getContractRaw(channel, cid);
		return new Contract(rawContract);
	}
	
	public String getContractRaw(String channel, String cid) {
		
		// try to first load it from db - quicker
		String rawContract = loadContractFromDb(channel, cid);
		if (rawContract != null)
			return rawContract;
		
		// it wasn't in db! try to retrieve it from blockchain - slower
		rawContract = loadContractFromBlockchain(channel, cid);
		
		// save contract to db for future times
		if (rawContract != null)
			saveRawContract(channel, cid, rawContract);
		return rawContract;
	}
	
	private String loadContractFromDb(String channel, String cid) {
		
		// recreate key
		final DBObject key = new BasicDBObject()
				.append("key", new BasicDBObject()
					.append("channelId", channel)
					.append("contractId", cid));
		
		// search and return contract if we have it in db
		DBCursor cursor = contractCollection.find(key);
		
		if (cursor.count() == 0) 
			return null;
		
		return (String) cursor.one().get("contract");
	}
	
	private String loadContractFromBlockchain(String channel, String cid) {
		
		// set the correct channel
		dpt.changeChannel(channel);
		
    	// query the chaincode
    	String rawJsonContract = null;
    	try {
    		rawJsonContract = dpt.callChaincodeFunction(
				Dispatcher.CHAINCODE_QUERY_OPERATION, 
				cid, 
				"getContractDefinition", 
				new String[] {}								// empty args
			);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
		return rawJsonContract;
	}

}
