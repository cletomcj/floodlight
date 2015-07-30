
package net.floodlightcontroller.noarpspoof;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


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
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.topology.ITopologyService;

import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * This is the main class of the "NoArpSpoof" module. This module is an example of how 
 * a SDN controller can mitigate ARP Spoof attacks.
 * 
 * 1) For each PACKET_IN received, this module extracts the data encapsulated in it.
 * 
 * 2) If the data is an ARP message, then it extracts the IP sender field
 * 
 * 3) Once it has learned that IP address, it finds out if that IP address belongs to 
 *    some device currently connected to OpenFlow network. To do this, the module asks
 *    "Device Manager" for all devices connected
 *    
 * 4) In case of one device has that IP address, the module finds out if that device is attached
 *    in the same port and switch where the ARP message was received. In affirmative case, the PACKET_IN
 *    is processed normally via "Command.CONTINUE", and if not, the module sends a FLOW_MOD message to the switch 
 *    to install a new flow entry that discards all packets from the port where the ARP message was received.
 *    
 *    In this way, the attacker will remain isolated until the attack is over (flow entries are configured
 *    with 5 sec "idle_timeout" by default).
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */

public class NoArpSpoof implements IFloodlightModule, IOFMessageListener {
	
    protected static Logger log = LoggerFactory.getLogger(NoArpSpoof.class);

    // Module dependencies
    protected IFloodlightProviderService floodlightProviderService;
	protected ITopologyService topologyService;
	protected IDeviceService deviceManagerService;
	protected IOFSwitchService switchService;
	
    //flow-mod defaults
    protected static short FLOWMOD_IDLE_TIMEOUT = 5; // in seconds
    protected static short FLOWMOD_HARD_TIMEOUT = 0; // infinite
    protected static short FLOWMOD_PRIORITY = 100;
    
    // flow-mod - for use in the cookie
    public static final int NO_ARP_SPOOF_APP_ID = 1;
    public static final int APP_ID_BITS = 12;
    public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
    public static final long NO_ARP_SPOOF_COOKIE = (long) (NO_ARP_SPOOF_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

 	
    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProviderService floodlightProviderService) {
        this.floodlightProviderService = floodlightProviderService;
    }
    
    @Override
    public String getName() {
        return "noarpspoof";
    }
    
    
    /*
     * Auxiliary method to extract IP sender field from an ARP message
     */
	public IPv4Address getSenderIp(Ethernet eth) {
		ARP arp = (ARP) eth.getPayload();
		if (arp.getProtocolType() == ARP.PROTO_TYPE_IP) {
			return IPv4Address.of(arp.getSenderProtocolAddress());
		}
		return IPv4Address.NONE;
	}
	
	/*
	 * Auxiliary method to extract the source MAC address from an ARP message
	 */
	public MacAddress getSenderMac(Ethernet eth) {
		ARP arp = (ARP) eth.getPayload();
		if (arp.getHardwareType() == ARP.HW_TYPE_ETHERNET) {
			return MacAddress.of(arp.getSenderHardwareAddress());
		}
		return MacAddress.NONE;
	}
	
	/*
	 * Auxiliary method to send a FLOW_MOD message to the switch in order to discard all packets
	 * like the fake ARP message 
	 */
    private void dropFlowMod(IOFSwitch sw, Match match) {

        OFFlowMod.Builder fmb;
		List<OFAction> actions = new ArrayList<OFAction>(); // set no action to drop

        fmb = sw.getOFFactory().buildFlowAdd();
        fmb.setMatch(match);
        fmb.setIdleTimeout(NoArpSpoof.FLOWMOD_IDLE_TIMEOUT);
        fmb.setHardTimeout(NoArpSpoof.FLOWMOD_HARD_TIMEOUT);
        fmb.setPriority(NoArpSpoof.FLOWMOD_PRIORITY);
        fmb.setCookie((U64.of(NoArpSpoof.NO_ARP_SPOOF_COOKIE)));
        fmb.setBufferId(OFBufferId.NO_BUFFER);        
        fmb.setActions(actions);

        // and write it out
        sw.write(fmb.build());
    }
    

    /*
     * This method checks if the ARP message encapsulated into the PACKET_IN is a fake message or not. 
     * To do that, first it extracts the IP sender field, then it checks if that IP address belongs to some 
     * device connected to the OpenFlow network. Finally, if there is some device with that IP address attached
     * to the network, the method checks if the switch and port where is the device is connected are the same
     * switch and port where the ARP message was received.
     */
    private Command processArpMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {

		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        //If the payload it's not an ARP message -> jump to the next module in the Floodlight pipeline
        if (!(eth.getPayload() instanceof ARP)){
        	return Command.CONTINUE;
        }
        
        Match m = pi.getMatch();
        OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : m.get(MatchField.IN_PORT));
        String dpid = sw.getId().toString();
        MacAddress sourceMac = this.getSenderMac(eth);
        IPv4Address sourceIp = this.getSenderIp(eth);
        
        if (log.isDebugEnabled()) {
			log.debug("ARP received from switch {} *** in_port {} *** sender_mac={}" +
					" *** sender_ip={} ***", new Object[] {dpid, inPort, sourceMac.toString(),
					sourceIp.toString()});
		}
        
        //Check if there is some device with that IP address
        Iterator<? extends IDevice> devices = deviceManagerService.queryDevices(null, null,sourceIp, null, null);
        //if no -> don't do anything
    	if (!devices.hasNext()){
    		if(log.isDebugEnabled()){
    			log.debug("THERE AREN'T DEVICES WITH THAT IP");
    		}
    	}
    	
    	//A device with that IP has been found  
    	while(devices.hasNext()) {
    		Device device = (Device) devices.next();
    		//check if the device is currently attached to the network
    		if(device.getAttachmentPoints().length== 0){
        		if (log.isDebugEnabled()) {
        			log.debug("IP IS CURRENTLY DISCONNECTED");
        		}
        		return Command.CONTINUE;
    		}
    		String swId= device.getAttachmentPoints()[0].getSwitchDPID().toString();
    		Integer swPort = device.getAttachmentPoints()[0].getPort().getPortNumber();
            //Check if the ARP message comes from that device or not
    		if (!((swId.equals(dpid)) && (swPort == inPort.getPortNumber()))){
        		if (log.isDebugEnabled()) {
        			log.debug("FAKE ARP MESSAGE!!!!! IP {} ARP message switch {} ARP message port {}" +
					" Device switch {} Device port {}", new Object[] {sourceIp.toString(), dpid, inPort, swId, swPort});
        		}
        		//It's a fake AR message so install new flow entry in order to discard all these fake packets
                this.dropFlowMod(sw, m);
                
        		return Command.STOP;
    		}
    	}
    	
        return Command.CONTINUE;
    }
    
    //IOFMessageListener implementation
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        if (msg.getType() == OFType.PACKET_IN) {
        	return this.processArpMessage(sw, (OFPacketIn) msg, cntx);
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
    
    // IFloodlightModule

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// We don't export any services
		return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// We don't have any services
		return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(ITopologyService.class);
		l.add(IDeviceService.class);
        return l;
    }
    
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		topologyService = context.getServiceImpl(ITopologyService.class);
		deviceManagerService = context.getServiceImpl(IDeviceService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);

    }
    
    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
    }
    
}
    
    
