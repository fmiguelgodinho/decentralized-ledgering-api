package core.dto;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Contract {
	
	
	private Map<String,?> attributes;
	
	
	public Contract(String rawJsonContract) {
		// read json and put into an hashmap
		try {
			attributes = new ObjectMapper().readValue(rawJsonContract, HashMap.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

}
