package integration;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import util.Pair;
import util.NodeConnection;

public class Dispatcher {
	
	public static final int CHAINCODE_QUERY_OPERATION = 0;
	public static final int CHAINCODE_INVOKE_OPERATION = 1;
	public static int CHAINCODE_CALL_INTERVAL_MS = 500;
	
	public static final String BLOCKCHAIN_NEGOTIATION_MODE = "TLS";
	public static final String BLOCKCHAIN_SSL_PROVIDER = "openSSL";
	
	
    private static final Logger log = Logger.getLogger(Dispatcher.class);
    
    private HFClient client;
    private Map<String,Pair<Channel, NodeConnection[]>> channels;
    private String currChannel;
    
    public Dispatcher(String crtPath, String keyPath, String username, String mspId, String org, String channelName, NodeConnection[] nodesOnChannel) throws Exception {
    	
//      // create fabric-ca client
//      HFCAClient caClient = getHfCaClient("http://localhost:7054", null);
//
// 		// enroll or load admin
//      AppUser admin = getAdmin(caClient);
//      log.info(admin);

        // register and enroll new user
        HLFUser appUser = getUser(crtPath, keyPath, username, mspId, org);
        log.info(appUser);
       
        // get HFC client instance
        client = getHfClient();
        // set user context
        client.setUserContext(appUser);

        // get HFC first channel using the client
        channels = new HashMap<String,Pair<Channel, NodeConnection[]>>();
        Channel firstChannel = initChannel(channelName, nodesOnChannel);
        channels.put(channelName, new Pair(firstChannel, nodesOnChannel));
        log.info("Channel: " + firstChannel.getName());
        
        // set current channel
        currChannel = channelName;
    }
    
    // use HLFJavaClient.CHAINCODE_QUERY_OPERATION or HLFJavaClient.CHAINCODE_INVOKE_OPERATION
    public String callChaincodeFunction(int op, String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws InterruptedException {
    	
    	String rsp = null;
    	
    	try {
    		// call corresponding chaincode operation
    		if (op == CHAINCODE_QUERY_OPERATION) {
    			rsp = query(chaincodeId, chaincodeFn, chaincodeArgs);
    		} else if (op == CHAINCODE_INVOKE_OPERATION) {
    			invoke(chaincodeId, chaincodeFn, chaincodeArgs);
    		} else {
    			throw new IllegalArgumentException("Unrecognized operation: " + op + ". Use HLFJavaClient.CHAINCODE_QUERY_OPERATION or HLFJavaClient.CHAINCODE_INVOKE_OPERATION!");
    		}
			
		} catch (ProposalException | InvalidArgumentException e) {
			e.printStackTrace();
		} finally {
			Thread.sleep(CHAINCODE_CALL_INTERVAL_MS);
		}
    	
    	return rsp;
    }
    
    public void changeChannel(String newChannelName, NodeConnection[] nodesOnChannel) throws InvalidArgumentException, TransactionException {
    	
    	Pair<Channel,NodeConnection[]> existingChannelCfg = channels.get(newChannelName);
    	
    	// if channel does not exist, initialize new channel on the client side
    	if (existingChannelCfg == null) {
        	Channel newChannel = initChannel(newChannelName, nodesOnChannel);
            channels.put(newChannelName, new Pair(newChannel, nodesOnChannel));
    	} else {
    		// just update peer connections
    		existingChannelCfg.setRight(nodesOnChannel);
            channels.put(newChannelName, existingChannelCfg);
    	}
    	// set current channel
    	currChannel = newChannelName;
    }

    private String query(String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws ProposalException, InvalidArgumentException {
    	
        // get channel instance from client
        Channel channel = channels.get(currChannel).getLeft();
        
        // create chaincode request
        QueryByChaincodeRequest qpr = client.newQueryProposalRequest();
        
        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeId).build();
        qpr.setChaincodeID(CCId);
        
        // CC function to be called
        qpr.setFcn(chaincodeFn);
        qpr.setArgs(chaincodeArgs);
        Collection<ProposalResponse> res = channel.queryByChaincode(qpr);
        
        log.info("Sending query request, function '" + chaincodeFn + "' with arguments ['" + String.join("', '", chaincodeArgs) + "'], through chaincode '" + chaincodeId + "'...");
        
        // display response
        String stringResponse = null, prevResponse = null;
        for (ProposalResponse pres : res) {
        	prevResponse = stringResponse;
            stringResponse = new String(pres.getChaincodeActionResponsePayload());
            log.info(stringResponse);
            
            // redundant check to ensure nodes all return the same thing
            if (prevResponse != null && !stringResponse.equals(prevResponse)) {
            	throw new RuntimeException("Incoherent response from nodes!!!");
            }
        }
        
        return stringResponse;

    }
    
    private void invoke(String chaincodeId, String chaincodeFn, String[] chaincodeArgs) throws ProposalException, InvalidArgumentException {
    	
        // get channel instance from client
        Channel channel = channels.get(currChannel).getLeft();
        
        // create chaincode request
        TransactionProposalRequest tpr = client.newTransactionProposalRequest();
        
        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID CCId = ChaincodeID.newBuilder().setName(chaincodeFn).build();

        // CC function to be called
        tpr.setChaincodeID(CCId);
        tpr.setFcn(chaincodeFn);
        tpr.setArgs(chaincodeArgs);
        
        Collection<ProposalResponse> res = channel.sendTransactionProposal(tpr);
        
        log.info("Sending transaction proposal, function '" + chaincodeFn + "' with arguments ['" + String.join("', '", chaincodeArgs) + "'], through chaincode '" + chaincodeId + "'...");
        
        // display response
        for (ProposalResponse pres : res) {
            String stringResponse = "Response from endorser is: " + pres.getChaincodeActionResponseStatus();
            log.info(stringResponse);
        }
        
        log.info("Collecting endorsements and sending transaction...");

        // send transaction with endorsements
        channel.sendTransaction(res);
        log.info("Transaction sent.");
    }
    
    private Channel initChannel(String channelName, NodeConnection[] nodesOnChannel) throws InvalidArgumentException, TransactionException {
        
    	Channel channel = client.newChannel(channelName);
    	
    	for (int i = 0; i < nodesOnChannel.length; i++) {
    		File tlsCrt = Paths.get(nodesOnChannel[i].tlsCrtPath).toFile();
            
            if (!tlsCrt.exists())
            	throw new RuntimeException("Missing TLS cert files");
            
            Properties secPeerProperties = new Properties();
            secPeerProperties.setProperty("hostnameOverride", nodesOnChannel[i].name);
            secPeerProperties.setProperty("sslProvider", BLOCKCHAIN_SSL_PROVIDER);
            secPeerProperties.setProperty("negotiationType", BLOCKCHAIN_NEGOTIATION_MODE);
            secPeerProperties.setProperty("pemFile", tlsCrt.getAbsolutePath());
            
            if (nodesOnChannel[i].type == NodeConnection.PEER_TYPE) {
            	// peer name and endpoint in network
	            Peer peer = client.newPeer(nodesOnChannel[i].name, "grpcs://" + nodesOnChannel[i].host + ":"  + nodesOnChannel[i].port, secPeerProperties);
	            channel.addPeer(peer);
            } else if (nodesOnChannel[i].type == NodeConnection.ORDERER_TYPE) {
            	// orderer name and endpoint in network
                Orderer orderer = client.newOrderer(nodesOnChannel[i].name, "grpcs://" + nodesOnChannel[i].host + ":"  + nodesOnChannel[i].port, secPeerProperties);
                channel.addOrderer(orderer);
            }
            
            
            // TODO: remove this
            if (i == 0) {
	            // eventhub name and endpoint in fabcar network
	            EventHub eventHub = client.newEventHub("eventhub01", "grpcs://localhost:7053", secPeerProperties);
	            channel.addEventHub(eventHub);
            }
            // TODO ENDS HERE
    	}
    	
    	// init channel, finally
        channel.initialize();
        return channel;
    }

    /**
     * Create new HLF client
     *
     * @return new HLF client instance. Never null.
     * @throws CryptoException
     * @throws InvalidArgumentException
     */
    static HFClient getHfClient() throws Exception {
        // initialize default cryptosuite
        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        // setup the client
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(cryptoSuite);
        return client;
    }
    
    static HLFUser getUser(String certPath, String keyPath, String userId, String mspId, String org) throws Exception {
        HLFUser appUser = null;//tryDeserialize(userId);
        if (appUser == null) {
            //String enrollmentSecret = caClient.register(rr, registrar);
            //Enrollment enrollment = caClient.enroll(userId, enrollmentSecret);
            appUser = new HLFUser(userId, org, mspId, certPath, keyPath);
            //serialize(appUser);
        }
        return appUser;
    }


    // user serialization and deserialization utility functions
    // files are stored in the base directory

    /**
     * Serialize AppUser object to file
     *
     * @param appUser The object to be serialized
     * @throws IOException
     */
    static void serialize(HLFUser appUser) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(
                Paths.get(appUser.getName() + ".jso")))) {
            oos.writeObject(appUser);
        }
    }

    /**
     * Deserialize AppUser object from file
     *
     * @param name The name of the user. Used to build file name ${name}.jso
     * @return
     * @throws Exception
     */
    static HLFUser tryDeserialize(String name) throws Exception {
        if (Files.exists(Paths.get(name + ".jso"))) {
            return deserialize(name);
        }
        return null;
    }

    static HLFUser deserialize(String name) throws Exception {
        try (ObjectInputStream decoder = new ObjectInputStream(
                Files.newInputStream(Paths.get(name + ".jso")))) {
            return (HLFUser) decoder.readObject();
        }
    }
}