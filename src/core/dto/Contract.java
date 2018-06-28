package core.dto;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Contract {
	
	
	private Map<String,?> attributes;
	private boolean isSection;
	
	public Contract(Map<String, ?> attributes) {
		this.attributes = attributes;
		this.isSection = true;
	}
	
	@SuppressWarnings("unchecked")
	public Contract(String rawJsonContract) {
		// read json and put into an hashmap
		try {
			attributes = new ObjectMapper().readValue(rawJsonContract, HashMap.class);
			this.isSection = false;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public Contract getExtendedContractProperties() {
		return new Contract((Map<String,?>) attributes.get("extended-contract-properties"));
	}
	
	@SuppressWarnings("unchecked")
	public Contract getApplicationSpecificProperties() {
		return new Contract((Map<String,?>) attributes.get("application-specific-properties"));
	}
	
	public String getContractStringAttr(String key) {
		return (String) attributes.get(key);
	}
	
	public int getContractIntAttr(String key) {
		return (int) attributes.get(key);
	}
	
	public float getContractFloatAttr(String key) {
		return (float) attributes.get(key);
	}
	
	public boolean getContractBoolAttr(String key) {
		return (boolean) attributes.get(key);
	}
	
	@SuppressWarnings("unchecked")
	public List<String> getContractListAttr(String key) {
		return (List<String>) attributes.get(key);
	}
	
	public boolean conformsToStandard() {
		
		if (isSection)
			throw new UnsupportedOperationException("Conformance check can't be executed on a section of a contract!");
		
		// validate the existence of these two sections
		if (!attributes.containsKey("extended-contract-properties") || !attributes.containsKey("application-specific-properties")) {
			return false;
		}
		
		try {
			Contract ecp = this.getExtendedContractProperties();
			Contract asp = this.getApplicationSpecificProperties();
			
			if (ecp == null || asp == null)
				return false;
			
		} catch (Exception e) {
			// invalid cast or something
			return false;
		}
		
		return true;
	}

}
