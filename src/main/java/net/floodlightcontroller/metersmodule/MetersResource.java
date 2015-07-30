package net.floodlightcontroller.metersmodule;

import java.io.IOException;

import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;



/**
 * 
 * This class extracts JSON fields from HTTP request and execute the method "setMeter" exported by
 * MetersModule
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public class MetersResource extends ServerResource {
	
	protected static Logger log = LoggerFactory.getLogger(MetersResource.class);
    protected static DatapathId dpid;
    protected static int meterId;
    protected static int meterRate;
    protected static int dscpIn;
    protected static int portIn;
    protected static int portOut;
    protected static int precLevel; //Increment of "drop-precedence" field inside DSCP field

    @Post
    public String store(String fmJson) {
    	boolean dscpValid = false;
    	precLevel = -1;
    	int[] dscpList = {10,12,14,18,20,22,26,28,30,34,36,38};
        IMetersService msv = (IMetersService)getContext().getAttributes().get(IMetersService.class.getCanonicalName()); 
        jsonConverter(fmJson, msv);
        
        
        for(int i=0;i<dscpList.length;i++){
        	if(dscpList[i] == dscpIn){
        		dscpValid = true;
        		break;
        	}
        }
       
        if(!dscpValid){
        	return "El campo dscpIn debe ser del tipo Assured Forwarding: 10,12,14,18,20,22,26,28,30,34,36,38";
        }
        
        //The "drop-precendence" field can be increased by a maximum of 2
        if(precLevel > 2){
        	return "'precLev' can't be higher than 2 "; 
        }
        
        //AF12, AF22, AF32 and AF42 classes can't increase "drop-precendence" field more than 1
        if(((dscpIn == 12) || (dscpIn == 20)) || ((dscpIn == 28) || (dscpIn == 36))){
        	if (precLevel > 1){
        		return "With that 'dscpIn' the 'precLev' field can't be higher than 1";
        	}
        }
        
        //AF13, AF23, AF33 y AF43 classes can't increase the "drop-precendence" field
        if(((dscpIn == 14) || (dscpIn == 22)) || ((dscpIn == 30) || (dscpIn == 38))){
        	if (precLevel > 0){
        		return "With that 'dscpIn' the 'drop-precendence' can't be increased";
        	}
        }
        
        return msv.setMeter(dpid, meterId, meterRate, dscpIn, portIn, portOut, precLevel);
        
    }
    
    
    
    /**
     * 
     * Auxiliary method to extract the JSON fields
     * 
     * @param fmJson
     * @param sv
     */
    public static void jsonConverter(String fmJson, IMetersService sv) {
        
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		try {
			try {
				jp = f.createJsonParser(fmJson);
			} catch (JsonParseException e) {
				throw new IOException(e);
			}

			jp.nextToken();
			if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
				throw new IOException("Expected START_OBJECT");
			}

			while (jp.nextToken() != JsonToken.END_OBJECT) {
				if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
					throw new IOException("Expected FIELD_NAME");
				}

				String n = jp.getCurrentName();
				jp.nextToken();
				if (jp.getText().equals("")) {
					continue;
				}

				// This assumes user having dpid info for involved switches
				else if (n.equalsIgnoreCase("dpId")) {
					try {
						dpid = DatapathId.of(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse switch DPID: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

				else if (n.equalsIgnoreCase("meterId")) {
					try {
						meterId = Integer.parseInt(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse meterId: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("rate")) {
					try {
						meterRate = Integer.parseInt(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse rate: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("dscpIn")) {
					try {
						dscpIn = Integer.parseInt(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse dscpIn: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("portIn")) {
					try {
						portIn = Integer.parseInt(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse portIn: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("portOut")) {
					try {
						portOut = Integer.parseInt(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse portOut: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("precLev")) {
					try {
						precLevel = Integer.parseInt(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse precLev: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

			}
				
		}catch (IOException e) {
		log.error("Unable to parse JSON string: {}", e);
		}		
    }
}
