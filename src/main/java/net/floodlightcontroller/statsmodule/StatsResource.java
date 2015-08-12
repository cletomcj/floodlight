package net.floodlightcontroller.statsmodule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
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
 * This class translates the JSON data of the HTTP request and calls the appropriate method of
 * the service exported by the "StatsModule"
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public class StatsResource extends ServerResource{
	
    protected static Logger log = LoggerFactory.getLogger(StatsResource.class);
    protected static DatapathId srcId;
    protected static DatapathId dstId;
    protected static IPv4Address srcIp;
    protected static IPv4Address dstIp;
    protected static int loss;

    
    @Post
    public String store(String fmJson) {
    
        IStatsService sv = (IStatsService)getContext().getAttributes().get(IStatsService.class.getCanonicalName());
        String op = (String) getRequestAttributes().get("op");
        
        jsonConverter(fmJson, sv);
        
        //REST API obtains the current loss of the packet-flow's path
        if (op.equalsIgnoreCase("request")) {
            return sv.getLoss(srcIp, dstIp);
        }
        // REST API activates loss automatic loss control on the specific packet flow
        if(op.equalsIgnoreCase("apply")){
    		return sv.activaQoS(srcIp, dstIp,loss);    
        }
        
        //REST API deactivates the automatic loss control on the specific packet flow
        if (op.equalsIgnoreCase("stop")) {
            return sv.desactivaQoS(srcIp, dstIp);
        }
        
        // REST API obtains all possible paths between two switches
        if(op.equalsIgnoreCase("paths")){ 
        	ArrayList<Stack<NodePortTuple>> paths = sv.getAllPaths(srcId, dstId);
        	String s = "";
        	for (Stack<NodePortTuple> path:paths){
        		s += "----- Ruta ------" + "\n";
        		for (NodePortTuple np:path){
        			s += "dpid: " + np.getNodeId().toString() + "--- port: " + np.getPortId().getPortNumber() + "\n";
        		}
        	}
        	return s;
        }
        
        // REST API obtains delay between two switches
        if(op.equalsIgnoreCase("delay")){ 
        	LinkDelay ld = sv.getDelay(srcId, dstId);
        	if (ld.getDelay()==0){
        		return "El delay es inferior a 1 ms!!";
        	}
        	//nanoseconds to miliseconds
        	double del = 0.000001*(ld.getDelay());
        	del = Math.round(del * 1000.0) / 1000.0; //rounded with 3 decimals
        	return ld.getSrc().toString()+" - "+ld.getDst().toString()+"\n"
            		 + "Delay: "+ del + "ms\n";
        }
        
        //REST API obtains the total delay along the current path of the packet flow
        if(op.equalsIgnoreCase("tdelay")){ 
        	ArrayList<LinkDelay> list = sv.getTotalDelay(srcIp, dstIp);
        	String s = "Retardos de la ruta: \n";
        	for(LinkDelay ld:list){
        		if(ld.getDelay() == 0){
        			s += "--------------------- \n";
        			s += "Delay inferior a 1 ms \n";
        		}else{
                	double del = 0.000001*(ld.getDelay());
                	del = Math.round(del * 1000.0) / 1000.0;
                	s += "--------------------- \n";
                	s += ld.getSrc().toString()+" - "+ld.getDst().toString()+"\n"
                        + "Delay: "+ del + "ms\n";
        		}
        	}
        	return s;	
        }
        
        return "Comando erroneo\n";
        
        
    }
    
    /**
     * 
     * Auxiliary method obtain the JSON fields and their values
     * 
     * @param fmJson
     * @param sv
     */
    public static void jsonConverter(String fmJson, IStatsService sv) {
        
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
				else if (n.equalsIgnoreCase("srcDpid")) {
					try {
						srcId = DatapathId.of(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse switch DPID: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}

				else if (n.equalsIgnoreCase("dstDpid")) {
					try {
						dstId = DatapathId.of(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse switch DPID: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("srcIp")) {
					try {
						srcIp = IPv4Address.of(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse IP srcIp: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("dstIp")) {
					try {
						dstIp = IPv4Address.of(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse IP dstIp: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
				
				else if (n.equalsIgnoreCase("loss")) {
					try {
						loss = Integer.parseInt(jp.getText());
					} catch (NumberFormatException e) {
						log.error("Unable to parse loss: {}", jp.getText());
						//TODO should return some error message via HTTP message
					}
				}
			}
				
		}catch (IOException e) {
		log.error("Unable to parse JSON string: {}", e);
		}		
    }
}
		
