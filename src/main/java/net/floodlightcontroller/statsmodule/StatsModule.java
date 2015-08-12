package net.floodlightcontroller.statsmodule;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Timer;
import java.util.concurrent.TimeUnit;


import net.floodlightcontroller.core.Deliverable;
import net.floodlightcontroller.core.DeliverableListenableFuture;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;


/**
 *   
 * This module allows user to perform several functions:
 * 1) To obtain dalays between two switches 
 * 2) To obtain the total delay alog the path of one packet flow
 * 3) To obtain all possible paths between two switches
 * 4) To activate an automatic loss control on a flow packet that is able to swich packet flow
 *    into an alternative path when the packet flow exceed a loss threshold     
 * 
 * Author: Carlos Martin-Cleto Jimenez
 * 
 */

public class StatsModule implements IFloodlightModule, IStatsService, IOFMessageListener{
	
    protected static Logger log = LoggerFactory.getLogger(StatsModule.class);
    protected static Stack<NodePortTuple> route;
    protected static ArrayList<Stack<NodePortTuple>> routesList;
    protected static Stack<DatapathId> nvisited;
    protected static HashMap<String,QoSFlow> qosFlows;
    protected static HashMap<String,Timer> timerFlows;
    
    
    protected static int COUNT = 0;

    // Module dependencies
    protected IFloodlightProviderService floodlightProviderService;
	//protected ITopologyService topologyService;
	protected IDeviceService deviceManagerService;
	protected IRoutingService routingService;
	protected IOFSwitchService switchService;
	protected ILinkDiscoveryService ldService;
	protected IRestApiService restApi;
	
    //flow-mod default
    protected static final short FLOWMOD_IDLE_TIMEOUT = 0; // infinite
    protected static final short FLOWMOD_HARD_TIMEOUT = 0; // infinite
    protected static final short FLOWMOD_PRIORITY = 100;
    
    // flow-mod - for use in the cookie
    public static final int QOS_APP_ID = 1;
    public static final int APP_ID_BITS = 12;
    public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
    public static final long APP_COOKIE = (long) (QOS_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;
	
    //Auxiliary variable to return a "Future<Long>" into the "getDelay" method when the PACKET_IN is received 
	protected static Deliverable<Long> deliverable;
	
	//Auxiliary variable that stores the timestamp when the PACKET_OUT is sent in the "getDelay" method
	protected static long startTime;
    
	
    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProviderService floodlightProviderService) {
        this.floodlightProviderService = floodlightProviderService;
    }
    
    @Override
    public String getName() {
        return "statsmodule";
    }
    
    
    /**
     * 
     * Method that returns all possible paths between two switches. It uses a "Depth 
     * First Search" algorithm and backtracking recursion. It stores all possible paths
     * into the global variable "routesList".
     * 
     * @param current - current root node into the DFS algorithm
     * @param target - destination switch where all paths must end
     * @param visited - nodes list that have already been visited into the DFS algorithm
     */
    private void searchDfs(DatapathId current, DatapathId target, Stack<DatapathId> visited){
    	    	
    	//To avoid loops
    	if(visited.contains(current) && !current.equals(target)){
    		//current node has already been visited
    		return;
    	}
    	    	
    	if(current.equals(target)){
    		log.info("Nodo destino alcanzado!! {}", current.toString());
    		//the target is reached -> add the new path into the paths list
            Stack<NodePortTuple> temp = new Stack<NodePortTuple>();
            for (NodePortTuple np : route){
                temp.add(np);
            }
            routesList.add(temp);
    		return;
    	}
    	
		nvisited.push(current); 	
    	Map<DatapathId, Set<Link>> allLinks = ldService.getSwitchLinks();
    	for(Link l:allLinks.get(current)){
    		//there are two links (one per direction) in each physichal connection
    		if(l.getDst().equals(current)){
    	    	// link inverted (we only wants one of both links)
    	    	continue;
    		}
    	    //add the next hop to the current path
	        route.push(new NodePortTuple(l.getSrc(), l.getSrcPort()));
	        route.push(new NodePortTuple(l.getDst(), l.getDstPort()));
			searchDfs(l.getDst(),target,nvisited);
			//if we have returned here it means that we have already finished exploring
			//the whole subtree and didn't find the target. We return to the root iteration
	        route.pop();
	        route.pop();
    	}
    	
    	nvisited.pop();    	
    }
    
    /**
     * Sends asynchronously a STATS_REQUEST message to the switch "sw" and returns the answer received
     * into a STATS_REPLY message
     * 
     * @param sw - switch being requested for statistics of one packet flow
     * @param m - OF match fields that defines the packet flow whose stats are being requested
     * @return message OF_STATS_REPLY from switch
     */
    @SuppressWarnings("unchecked")
	public List<OFStatsReply> sendFeatReq(IOFSwitch sw, Match m){
    	
		ListenableFuture<?> future;
		List<OFStatsReply> values = null;
    	
        OFFlowStatsRequest req = sw.getOFFactory().buildFlowStatsRequest()
        		.setMatch(m)
				.setOutPort(OFPort.ANY)
				.setTableId(TableId.of(0))
				.build();
        
        try {
			future = sw.writeStatsRequest(req);
			values = (List<OFStatsReply>) future.get(4, TimeUnit.SECONDS);

		} catch (Exception e) {
			log.error("Failure retrieving statistics from switch " + sw, e);
		}
        
    	return values;
	}
        
    //--------------------------------------------------
    // Implementation of the IOFMessageListener interface
    //--------------------------------------------------
    /**
     * 
     * This method gets the PACKET_IN messages that are generated when we are measuring a delay between 
     * two switches.
     * 
     */
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    	
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		if (eth.getSourceMACAddress().equals(MacAddress.of("00:00:00:00:00:aa"))){
	    	long endTime = System.nanoTime();
	    	deliverable.deliver(new Long(endTime));
		    return Command.STOP; 
			
        }else{
        	return Command.CONTINUE;	
        }		
    }
    
    
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }
    
    
    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }
    //---------------------------------------------------------------
    // End of the implementation of the IOFMessageListener interface
    //---------------------------------------------------------------
    
    
    //------------------------------------------------
    // Implementation of the IFloodlightModule interface
    //------------------------------------------------
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
    	//We exports the IStatsService interface to be able to the REST API module 	
        Collection<Class<? extends IFloodlightService>> l = 
        		new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IStatsService.class);
		return l;
		
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
    	//We exports the IStatsService interface to be able to the REST API module 	    	
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = 
        		new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IStatsService.class, this);
        return m;
        
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
		l.add(IDeviceService.class);
		l.add(IRoutingService.class);
		l.add(IOFSwitchService.class);
		l.add(ILinkDiscoveryService.class);
	    l.add(IRestApiService.class);
        return l;
    }
    
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		deviceManagerService = context.getServiceImpl(IDeviceService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		ldService = context.getServiceImpl(ILinkDiscoveryService.class);
	    restApi = context.getServiceImpl(IRestApiService.class);
		routesList = new ArrayList<Stack<NodePortTuple>>();
		route = new Stack<NodePortTuple>();
		nvisited = new Stack<DatapathId>();
		qosFlows = new HashMap<String,QoSFlow>();
		timerFlows = new HashMap<String,Timer>();
		deliverable = null;
		
		


    }
    
    @Override
    public void startUp(FloodlightModuleContext context) {
        // register REST interface
        restApi.addRestletRoutable(new StatsWebRoutable());
        
        floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
        
    }
    //--------------------------------------------
    //End of the IOFFloodlightModule interface
    //--------------------------------------------
    
    
    
    //------------------------------------------------
    // Implementation of the IStatsService interface
    //------------------------------------------------ 
    
    /**
     * 
     * Method that is called by the RES API when the user wants to know the current loss between two
     * hosts. 
     * 
     * @param srcIp - source IP addres of the packet flow
     * @param dstIp - destination IP address of the packet flow
     * @return loss along the path between srcIp and dstIp
     */
	@Override
	public String getLoss(IPv4Address srcIp, IPv4Address dstIp){

		NodePortTuple srcPoint = getAttachment(srcIp);
		NodePortTuple dstPoint = getAttachment(dstIp);
		DatapathId srcDpid = srcPoint.getNodeId();
		DatapathId dstDpid = dstPoint.getNodeId();
		IOFSwitch sw1 = switchService.getSwitch(srcDpid);
		IOFSwitch sw2 = switchService.getSwitch(dstDpid);
		
    	Match myMatch1 = sw1.getOFFactory().buildMatch()
    			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    			.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.1.0.2"))
    			.setExact(MatchField.IPV4_DST, IPv4Address.of("10.1.0.7"))
    			.build();
    	
    	Match myMatch2 = sw2.getOFFactory().buildMatch()
    			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    			.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.1.0.2"))
    			.setExact(MatchField.IPV4_DST, IPv4Address.of("10.1.0.7"))
    			.build();
    	
		
    	List<OFStatsReply> l1 = this.sendFeatReq(sw1, myMatch1);
    	List<OFStatsReply> l2 = this.sendFeatReq(sw2, myMatch2);
    	
    	long packetsIn = 0;
    	long packetsOut = 0;
    	
    	for(OFStatsReply reply:l1){
    		List<OFFlowStatsEntry> entries1 = ((OFFlowStatsReply)reply).getEntries();
    		for(OFFlowStatsEntry entry:entries1){
    			packetsIn += entry.getPacketCount().getValue();
    		}    		
    	}
    	
    	for(OFStatsReply reply:l2){
    		List<OFFlowStatsEntry> entries2 = ((OFFlowStatsReply)reply).getEntries();
    		for(OFFlowStatsEntry entry:entries2){
    			packetsOut += entry.getPacketCount().getValue();
    		}    		
    	}
    	
    	String str = "Sent packets: " + packetsIn + "\n"
    			      + "Received packets: " + packetsOut + "\n"
    			      + "Loss: " + ((packetsIn - packetsOut + 1)/packetsIn)*100 + "%\n";
    	
    	return str;
    		
	}
	
	/**
	 * 
	 * This method is called by the REST API when the user activates the automatic loss control on a packet flow.
	 * 
	 * 1) The packet flow to be monitored is added to the QoSFlows list
	 * 
	 * 2) A new TimerTask "QoSTimer" is created and exceuted periodically each 5 seconds. That TimerTask
	 *    is going to check every 5 seconds if the packet flow is exceeding the loss thershold. 
	 *    
	 *    In case of packet flow exceeds loss thershold, then, the TimerTask will execute a method that 
	 *    sends FLOW_MOD messages to remove the old flow entries of switches and install the new ones 
	 *    to switch the path of the packet flow.
	 * 
	 * 
	 * @param srcIp - source IP address of the packet flow
	 * @param dstIp - destination IP address of the packet flow
	 * @param l - loss theshold (%)
	 * @return String indicating that QoS has been activated on that flow
	 *  
	 */
		@Override
		public String activaQoS(IPv4Address srcIp, IPv4Address dstIp, int l){
			
			//Here, we must check that there isn't another packet flow with those IP addresses
			String flowKey = srcIp.toString()+dstIp.toString();
			if(timerFlows.containsKey(flowKey)){
				return "QoS ya ha sido añadida a este flujo \n";
			}
						
			//First, we extract the attachment points of the IP addresses
			NodePortTuple srcPoint = getAttachment(srcIp);
			NodePortTuple dstPoint = getAttachment(dstIp);
			DatapathId srcDpid = srcPoint.getNodeId();
			DatapathId dstDpid = dstPoint.getNodeId();
			IOFSwitch sw1 = switchService.getSwitch(srcDpid);
			IOFSwitch sw2 = switchService.getSwitch(dstDpid);
			
			//We extract the default path
			Route r = routingService.getRoute(srcDpid, srcPoint.getPortId(), dstDpid, dstPoint.getPortId(), U64.of(0));
			List<NodePortTuple> defPath = r.getPath(); //ruta por defecto del flujo
			
			//Add the packet flow to the list of monitored packet flows and obtain all possible paths
			//between the source switch and the destinations switch
		    QoSFlow qosflow = new QoSFlow(srcIp, dstIp);
	        routesList.clear(); //reset auxiliary variable
	        route.clear();//reset auxiliary variable
	        nvisited.clear();
		    route.add(0, srcPoint);
	        this.searchDfs(srcDpid,dstDpid,nvisited);
	        for(Stack<NodePortTuple> ruta:routesList){
	        	ruta.push(dstPoint);
	        	FlowRoute fr = new FlowRoute();
	        	fr.setPath(ruta);
	        	qosflow.addFlowRoute(fr);
	        	if(comparePaths(ruta, defPath)){
	        		qosflow.setRutaActual(fr);//This is the default path
	        		log.info("Establecemos esta ruta como la actual");
	        	}

	        }
	        
	        qosFlows.put(flowKey, qosflow);
	        
	    	Match m1 = sw1.getOFFactory().buildMatch()
	    			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
	    			.setExact(MatchField.IPV4_SRC, srcIp)
	    			.setExact(MatchField.IPV4_DST, dstIp)
	    			.build();
	    	
	    	Match m2 = sw2.getOFFactory().buildMatch()
	    			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
	    			.setExact(MatchField.IPV4_SRC, srcIp)
	    			.setExact(MatchField.IPV4_DST, dstIp)
	    			.build();
	        
	    	//We initizalize the TimerTask here
	        QoSTimer qosTask =  new QoSTimer(srcIp, dstIp, m1, m2, sw1, sw2, l) {
	        	@Override
	        	public void run() 
	            {
	    	    	List<OFStatsReply> l1 = sendFeatReq(this.sw1, this.match1);
	    	    	List<OFStatsReply> l2 = sendFeatReq(this.sw2, this.match2);
	    	    	
	    	        double packetsIn = 0;
	    	    	double packetsOut = 0;
	    	    	boolean buenaMedida = true;
	    	    	double lost = 0;
	    	    	
	    	    	OFStatsReply reply1 = l1.get(0);
	    	    	List<OFFlowStatsEntry> entries1 = ((OFFlowStatsReply)reply1).getEntries();
	    	    	log.info("Numero de replies de sw1: {}", entries1.size());
	    	    	//To avoid null pointer exception when packet flow stops and flow entries automatically expires
	    	    	if(entries1.size() > 0){ 
	    	    		packetsIn = (double)(entries1.get(0).getPacketCount().getValue());
	    	    	}else{
	    	    		//To avoid bad measures when one of the STATS_REPLY hasn't been received properly
	    	    		buenaMedida = false;
	    	    	}
	    	    
	    	    	OFStatsReply reply2 = l2.get(0);
	    	    	List<OFFlowStatsEntry> entries2 = ((OFFlowStatsReply)reply2).getEntries();
	    	    	log.info("Numero de replies de sw2: {}", entries2.size());
	    	    	if(entries2.size() > 0){
	    	    		packetsOut = (double)(entries2.get(0).getPacketCount().getValue());
	    	    	}else{
	    	    		buenaMedida = false;
	    	    	}
	    	    
    	    		if(((packetsIn-packetsOut) > 0) && buenaMedida ){
    	    			lost = (packetsIn - packetsOut)/packetsIn;
    	    		}
	    	    	log.info("Sent packets: " + packetsIn + "\n"
	    	    			      + "Received packets: " + packetsOut + "\n"
	    	    			      + "Loss: " + lost*100 + "%\n");
	    	    	
	    	    	//If packet flow exceeds the loss threshold
	    	    	if(lost*100 > this.loss){
	    	    		String key = this.srcIp.toString()+this.dstIp.toString();
	    	    		FlowRoute oldRoute = qosFlows.get(key).getRutaActual();
	    	    		
	    	    		//recalculate new path
	    	    		FlowRoute newRoute = qosFlows.get(key).changeRoute();
	    	    		//delete old flow entries
	    		        pushRoute(oldRoute.getPath(), this.srcIp, this.dstIp, OFFlowModCommand.DELETE, false);
	    		        pushRoute(oldRoute.getPath(), this.srcIp, this.dstIp, OFFlowModCommand.DELETE, true);
	    		        log.info("OLD PATH DELETED");
	    		        //add new flow entries
	    		        pushRoute(newRoute.getPath(), this.srcIp, this.dstIp, OFFlowModCommand.ADD, false);
	    		        pushRoute(newRoute.getPath(), this.srcIp, this.dstIp, OFFlowModCommand.ADD, true);
	    		        log.info("NEW PATH CONFIGURED");
	    	        	for(NodePortTuple id:newRoute.getPath()){
	    					log.info(id.getNodeId().toString());
	    					log.info(""+id.getPortId().getPortNumber());
	    				}
	    	    	}
	    	    	    	    		    	    	
	            }	        	
	        };
	        
	        //We install  the default path with prioriy 100 and idle_timout =  15 to avoid that the flow entry 
	        //be deleted when in an interval of 5 second all packets are lost
	        pushRoute(qosflow.getRutaActual().getPath(), srcIp, dstIp, OFFlowModCommand.ADD, false);
	        pushRoute(qosflow.getRutaActual().getPath(), srcIp, dstIp, OFFlowModCommand.ADD, true);
	        //start the TimerTask periodically each 5 seconds
	        Timer timer = new Timer();
	        timer.scheduleAtFixedRate(qosTask, 0, 5000); 
	        timerFlows.put(qosflow.getKey(), timer);
	        return  "QoS activated on packet flow:" + srcIp.toString() + "--" + dstIp.toString()+ "\n";
	        			
		}
		
		/**
		 * 
		 * Method that is called from REST API to deactivate the automatic loss control
		 * @param srcIp 
		 * @param dstIp
		 * 
		 */
		@Override
		public String desactivaQoS(IPv4Address srcIp, IPv4Address dstIp){
			String key = srcIp.toString()+dstIp.toString();
			if(!qosFlows.containsKey(key)){
				return "Dicho flujo no tiene activada QoS";
			}
    		FlowRoute oldRoute = qosFlows.get(key).getRutaActual();
	        pushRoute(oldRoute.getPath(), srcIp, dstIp, OFFlowModCommand.DELETE, false);
	        pushRoute(oldRoute.getPath(), srcIp, dstIp, OFFlowModCommand.DELETE, true);
			timerFlows.get(key).cancel();
			timerFlows.remove(key);
			qosFlows.remove(key);
			return "QoS desactivada para el flujo:" + srcIp.toString() + "--" + dstIp.toString()+ "\n";
		}
		
		
		public ArrayList<Stack<NodePortTuple>> getAllPaths(DatapathId srcDpid, DatapathId dstDpid){
	        route.clear();
	        routesList.clear();
	        nvisited.clear();
	        this.searchDfs(srcDpid,dstDpid,nvisited);
	        return routesList;
		}
		
		/**
		 * Method to send FLOW_MOD messages (ADD or DELETE) to all the switches of the given path. 
		 * @param path - path where to send the FLOW_MOD
		 * @param match OFMatch to identify the packet flow
		 * @param cookie - to identify the flow entries when is need to delete them
		 * @param cntx
		 * @param flowModCommand - ADD or DELETE
		 * @param inverted - shows if the flow entries match the same direction that the packet flow(true) or not (false)
		 * @return
		 */
		public void pushRoute(Stack<NodePortTuple> path, IPv4Address srcIp, IPv4Address dstIp, 
				OFFlowModCommand flowModCommand, boolean inverted) {
			
			for (int i = 0; i<path.size()-1; i=i+2) {
				
				DatapathId switchDPID = path.get(i).getNodeId();
				IOFSwitch sw = switchService.getSwitch(switchDPID);
				
				OFFlowMod.Builder fmb;
				switch (flowModCommand) {
				case ADD:
					fmb = sw.getOFFactory().buildFlowAdd();
					break;
				case DELETE:
					fmb = sw.getOFFactory().buildFlowDeleteStrict();
					break;
				default:
					fmb = sw.getOFFactory().buildFlowAdd();        		
				}
				
				OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
				List<OFAction> actions = new ArrayList<OFAction>();
				OFPort outPort;
				OFPort inPort;
				Match.Builder mb = sw.getOFFactory().buildMatch();
				
				if (inverted){
					outPort = path.get(i).getPortId();
					inPort = path.get(i+1).getPortId();
	    			mb.setExact(MatchField.IPV4_SRC, dstIp); 
	    			mb.setExact(MatchField.IPV4_DST, srcIp);
					
				}else{
					outPort = path.get(i+1).getPortId();
					inPort = path.get(i).getPortId();
	    			mb.setExact(MatchField.IPV4_SRC, srcIp);
	    			mb.setExact(MatchField.IPV4_DST, dstIp);
				}
				
				mb.setExact(MatchField.IN_PORT, inPort);
				mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);		    	
				aob.setPort(outPort);
				aob.setMaxLen(Integer.MAX_VALUE);
				actions.add(aob.build());
				
				fmb.setMatch(mb.build())
				.setActions(actions)
				.setIdleTimeout(FLOWMOD_IDLE_TIMEOUT)
				.setHardTimeout(FLOWMOD_HARD_TIMEOUT)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie((U64.of(StatsModule.APP_COOKIE)))
				.setOutPort(outPort)
				.setPriority(FLOWMOD_PRIORITY);
				
				sw.write(fmb.build());
				//sw.flush();
		
			}
						
		}
				
		
		/**
		 * Auxiliary method to compare a Stack<NodePortTuple> and a List<NodePortTuple>
		 * @param s
		 * @param l
		 * @return
		 */
		public boolean comparePaths(Stack<NodePortTuple> s, List<NodePortTuple> l){
			Set<NodePortTuple> s1 = new HashSet<NodePortTuple>();
			Set<NodePortTuple> s2 = new HashSet<NodePortTuple>();
			s1.addAll(s);
			s2.addAll(l);
			return s1.equals(s2);
		}
		
		/**
		 * Auxiliay method that returns the switch and port where an IP address is connected
		 * @param ip
		 * @return
		 */
		public NodePortTuple getAttachment(IPv4Address ip){
			OFPort port = OFPort.ZERO;
			DatapathId swId = null;
	        Iterator<? extends IDevice> devices = deviceManagerService.queryDevices(null, null,ip, null, null);
	    	if (!devices.hasNext()){
	    			log.info("ANY DEVICE FOUND WITH THAT IP: {}", ip.toString());
	    			return new NodePortTuple(swId, port);
	    	}
	    	while(devices.hasNext()) {
	    		Device device = (Device) devices.next();
	    		//the device must be connected to the network
	    		if(device.getAttachmentPoints().length== 0){
	        			log.info("IP {} SIN CONEXION EN NINGUN SWITCH",ip.toString());
	        			return new NodePortTuple(swId, port);
	        	}
	    		swId = device.getAttachmentPoints()[0].getSwitchDPID();
	    		port = device.getAttachmentPoints()[0].getPort();
	    	}
			return new NodePortTuple(swId, port);
		}
		
		/**
		 * 
		 * Method to obtain the total delay of the packet-flow's path. FIrst it obtains the current path
		 * of the packet flow, and next it obtain the delay for each one of the hops between switches 
		 * along the path
		 * @param srcIp 
		 * @param dstIp 
		 * 
		 */
		@Override
		public ArrayList<LinkDelay> getTotalDelay(IPv4Address srcIp, IPv4Address dstIp){
			
			ArrayList<LinkDelay> delays = new ArrayList<LinkDelay>();
			
			NodePortTuple src = getAttachment(srcIp);
			NodePortTuple dst = getAttachment(dstIp);
			
			//if the flow hasn't been monitored it will always have the same path
			if(!timerFlows.containsKey(srcIp.toString()+dstIp.toString())){
				Route r = routingService.getRoute(src.getNodeId(), src.getPortId(), dst.getNodeId(), dst.getPortId(), U64.of(0));
				List<NodePortTuple> ruta = r.getPath();
				//Along the path, each switch appears twice (port in / port out) so we must follow tha path
				//from 2 to 2
				for(int i = 0; i<ruta.size()-2 ; i=i+2){
					LinkDelay ld = getDelay(ruta.get(i).getNodeId(), ruta.get(i+2).getNodeId());
					delays.add(ld);
				}
			//if it is a monitored flow, then we have to check what is the current path
			}else{
				FlowRoute r = qosFlows.get(srcIp.toString()+dstIp.toString()).getRutaActual();
				Stack<NodePortTuple> ruta = r.getPath();
				for(int i = 0; i<ruta.size()-2 ; i=i+2){
					LinkDelay ld = getDelay(ruta.get(i).getNodeId(), ruta.get(i+2).getNodeId());
					delays.add(ld);
				}
			}
							
			return delays;
				
	    }
		

		/**
		 *
		 * Method to obtain the delay between two switches. 
		 * 
		 * This method sends PACKET_OUT messages to switches and then switches return PACKET_IN messages
		 * that are going to be listened at "receive()" method. When the PACKET_IN is received, a
		 * Future object of Java is used so "receive" method can pass through the information to 
		 * the "getDelay" method.
		 * 
		 * First, this method calculates the delay between
		 * controller-switch and then calculate the delay between both switches
		 * 
		 */
		@Override
		public LinkDelay getDelay(DatapathId src, DatapathId dst){
						
			OFPort srcPort = null;
			LinkDelay ld = null;
			Set<Link> links = ldService.getSwitchLinks().get(src);
			for(Link l:links){
				if(l.getDst().equals(dst)){
					srcPort = l.getSrcPort();
					ld = new LinkDelay(l,0);
				}
			}			
			ListenableFuture<?> myFuture;
			Long l1 = new Long(0);
			Long l2 = new Long(0);
			Long delay = new Long(0);
			//delay controller-switch1 (T1)
			try {
				myFuture = creaFuture(src, OFPort.CONTROLLER);
				l1 = (Long)myFuture.get(10, TimeUnit.SECONDS);
				l1 = (long)(0.5*l1);
			} catch (Exception e) {
				log.error("Failure retrieving delay srcDpid-controller");
			}
			//delay controller-switch 2 (T2)
			try {
				myFuture = creaFuture(dst, OFPort.CONTROLLER);
				l2 = (Long)myFuture.get(10, TimeUnit.SECONDS);
				l2 = (long) (0.5*l2); 
			} catch (Exception e) {
				log.error("Failure retrieving delay dstDpid-controller");
			}
			//total delay T1 + T2 + delay between switches
			try {
				myFuture = creaFuture(src, srcPort);
				delay = (Long)myFuture.get(10, TimeUnit.SECONDS);
			} catch (Exception e) {
				log.error("Failure retrieving PACKET_IN from switch dstDpid ");
			}
			
			long result = delay.longValue() - l1 - l2;
			if(result < 1000000){
				ld.setDelay(0);
				return ld;
			}
			ld.setDelay(result);
			return ld;
		}
		
		
		/**
		 * 
		 * Auxiliary method that returns a ListenableFuture which returns the result (Long) when the method
		 * "deliver" of "deliverable" variable is called
		 * 		  
		 * @param src switch to which  send the PACKET_OUT message
		 * @param p - port where the packet must be forwarded
		 * @return
		 */
		public ListenableFuture<Long> creaFuture(DatapathId src, OFPort p){
									
	        final DeliverableListenableFuture<Long> future =
	                new DeliverableListenableFuture<Long>();
	        
	        deliverable =  new Deliverable<Long>() {
	            @Override
	            public void deliver(Long reply) {
	                    future.deliver(new Long(reply.longValue() - startTime));
	            }
	            @Override
	            public void deliverError(Throwable cause) {
	                future.deliverError(cause);
	            }
	            @Override
	            public boolean isDone() {
	                return future.isDone();
	            }
	            @Override
	            public boolean cancel(boolean mayInterruptIfRunning) {
	                return future.cancel(mayInterruptIfRunning);
	            }
	        };
	        
	    	enviaPktOut(src, p);
	    	log.info("timestamp al enviar: {}", startTime);
	    	log.info("Enviando packet out por el switch {} puerto {}", src.toString(), p.getPortNumber());
	    	return future;
	    	
		}
		
		/**
		 * 
		 * Method to generate an Ethernet packet witch source MAC 00:00:00:00:00:aa and
		 * desination MAC 00:00:00:00:00:bb and encapsulate it into a PACKET_OUT message. 
		 * 
		 * @param src - switch to send the PACKET_OUT message
		 * @param port - port of the PACKET_OUT message where the packet must be forwarded
		 */
		public void enviaPktOut(DatapathId src, OFPort port){
			
			IOFSwitch sw1 = switchService.getSwitch(src);
			
		    OFPacketOut.Builder pob = sw1.getOFFactory().buildPacketOut();
		    List<OFAction> actions = new ArrayList<OFAction>();
		    actions.add(sw1.getOFFactory().actions().output(port, Integer.MAX_VALUE));
		    //To not unset the Ethernet packet, we introduce an 8 bytes payload
		    long payload = 200;
	    	byte[] data = ByteBuffer.allocate(8).putLong(payload).array();
	    	
		    IPacket pkt = new Ethernet()
		    	.setDestinationMACAddress("00:00:00:00:00:bb")
		    	.setSourceMACAddress("00:00:00:00:00:aa")
		    	.setEtherType(Ethernet.TYPE_IPv4)
		    	.setPayload((new Data(data)));
		    			
			pob.setActions(actions);
	        pob.setBufferId(OFBufferId.NO_BUFFER);
	        pob.setData(pkt.serialize());
	        
	    	startTime = System.nanoTime();
	        sw1.write(pob.build());
			
		} 
		
}
    
    
