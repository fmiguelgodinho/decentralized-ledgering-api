package core;

import java.io.File;
import java.io.IOException;

import org.apache.commons.configuration2.Configuration;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import core.dto.ChaincodeResult;
import core.dto.Contract;
import core.exception.InvalidContractException;
import integration.Dispatcher;

public class ContractInterpreter {
		
	
	// TODO: update contracts in db if updated in bc?
	private DBCollection contractCollection;
	private Dispatcher dpt;
	
	public ContractInterpreter(Configuration cfg, MongoClient dbClient, Dispatcher dpt) {
		DB db = dbClient.getDB(cfg.getString("mongo.database"));
		contractCollection = db.getCollection(cfg.getString("mongo.contractCollection"));
		this.dpt = dpt;
	}
	
	public void saveRawContractToDB(String channel, String cid, String rawContract) {
		
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
			saveRawContractToDB(channel, cid, rawContract);
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
		
    	// query the chaincode
    	String rawJsonContract = null;
    	try {
    		ChaincodeResult cr = dpt.callChaincodeFunction(
				Dispatcher.CHAINCODE_QUERY_OPERATION, 
    			channel,
				cid, 
				"getContractDefinition", 
				new String[] {}								// empty args
			);
    		if (cr.getStatus() == ChaincodeResult.CHAINCODE_SUCCESS) {
    			rawJsonContract = cr.getContent();
    		}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
		return rawJsonContract;
	}
	
	public boolean deployContract(String cid, String cver, File csfolder, String cpath, String cspecs) throws InvalidContractException {
		
		// set the correct channel
//		dpt.changeChannel(channel);
		
		Contract contract = new Contract(cspecs);
		if (!contract.conformsToStandard())
			throw new InvalidContractException("Contract doesn't conform to standard!");
		
		// install chaincode
		try {
			dpt.install(cid, cver, csfolder, cpath, new String[] { cspecs }, contract.getExtendedContractProperties());
		} catch (InvalidArgumentException | ProposalException | IOException e) {
			e.printStackTrace();
		}
		
		// TODO: save to db after successful installation and instantiation
		
		return true;
	}

}
