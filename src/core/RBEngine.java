package core;

import com.mongodb.MongoClient;

public class RBEngine {

	private MongoClient dbClient;
	
	public RBEngine(MongoClient dbClient) {
		this.dbClient = dbClient;
	}
}
