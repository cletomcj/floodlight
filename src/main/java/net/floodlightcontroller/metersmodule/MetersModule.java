package net.floodlightcontroller.metersmodule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * This is the main class of the "MetersModule". This module allows to user to install into
 * an OpenFlow switch a flow meter and install a flow entry to attach that meter to specific flow. 
 * The user only needs to execute a REST command indicating: 
 * - Datapath ID of the switch
 * - The new meter ID
 * - Rate (kbps) of the new meter
 * - DSCP field that identifies the flow which is being applied the meter
 * - Input port of the switch
 * - Out port where flow packets are forwarding
 * - Increment of the DSCP field that is going to be applied to those packets which exceed the rate
 * 
 *  NOTE: If the user doesn't specify any increment to DSCP field, then the flow packets which exceed
 *  the rate, instead of being remarked will be directly discarded
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public class MetersModule implements IFloodlightModule, IMetersService{

    @SuppressWarnings("unused")
	private IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	protected IRestApiService restApi;
    
    protected static Logger log = LoggerFactory.getLogger(MetersModule.class);
    
    protected static int contador = 0;
    
    protected static final short FLOWMOD_IDLE_TIMEOUT = 0; // infinite
    protected static final short FLOWMOD_HARD_TIMEOUT = 0; // infinite
    protected static final short FLOWMOD_PRIORITY = 100;
    
    // flow-mod - for use in the cookie
    public static final int METER_APP_ID = 2;
    public static final int APP_ID_BITS = 12;
    public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
    public static final long APP_COOKIE = (long) (METER_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }
   
    /*
     * Auxiliary method to send a METER_MOD message to the switch and install a new 
     * flow meter
     */
    public void sendMeterMod(IOFSwitch sw, int meterId, int rate, int precLev){
    	
    	OFMeterBand mb;
    	short increment = (short)precLev;
    	
    	//If "precLev" hasn't been specified, then the flow packets which exceed the rate
    	// will be discarded
    	if (precLev < 0){
    		mb = sw.getOFFactory().meterBands().buildDrop()
    				.setRate(rate)
                    .build();
    	}else{
    	//Those flow packets which exceed the rate will have their DSCP field remarked
    		mb = sw.getOFFactory().meterBands().buildDscpRemark()
                .setRate(rate)
                .setPrecLevel(increment)
                .build();
    	}

        ArrayList<OFMeterBand> mbl = new ArrayList<OFMeterBand>();
        mbl.add(mb);
        OFMeterMod mm = sw.getOFFactory().buildMeterMod()
        		 .setFlags(1 << 0) //Rate is specified in kbps
                 .setMeters(mbl)
                 .setMeterId(meterId)
                 .setCommand(0)
                 .build();
         
        sw.write(mm);

    }
    
    /*
     * Auxiliary method to send a FLOW_MOD message to install a new  flow entry to 
     * attach a specific flow with an specific Meter ID and specify the output port where
     * the packets will be forwarded
     */
    public void sendFlowMod(IOFSwitch sw, int dscpIn, int portIn, int portOut, int meterId){
    	
    	List<OFAction> actions = new ArrayList<OFAction>();
    	OFActionOutput aob = sw.getOFFactory().actions().buildOutput()
    			.setPort(OFPort.of(portOut))
    			.build();
    	actions.add(aob);
			
		List<OFInstruction> instructions = new ArrayList<OFInstruction>();
		OFInstructionWriteActions wa = sw.getOFFactory().instructions().buildWriteActions()
				.setActions(actions)
				.build();
		instructions.add(wa);
					
		OFInstructionMeter im = sw.getOFFactory().instructions().buildMeter()
				.setMeterId(meterId)
				.build();
	    instructions.add(im);	
	    
	    String dscp = "DSCP_" + dscpIn;
		Match mb = sw.getOFFactory().buildMatch()
				.setExact(MatchField.IN_PORT,OFPort.of(portIn))
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_DSCP,IpDscp.valueOf(dscp))
				.build();
		
		OFFlowAdd flowEntry = sw.getOFFactory().buildFlowAdd()
				.setMatch(mb)
				.setInstructions(instructions)
				.setIdleTimeout(FLOWMOD_IDLE_TIMEOUT)
				.setHardTimeout(FLOWMOD_HARD_TIMEOUT)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie((U64.of(MetersModule.APP_COOKIE)))
				.setOutPort(OFPort.of(portOut))
				.setPriority(FLOWMOD_PRIORITY)
				.build();
				
				sw.write(flowEntry);  	
    }
    
    
    /*
     * This method will be called when the user execute the REST command to 
     * an OpenFlow switch a flow meter and install a flow entry to attach that meter to specific flow. 
     * The user only needs to execute a REST command indicating: 
     * dpid - Datapath ID of the switch
     * meterId - The new meter ID
     * meterRate - Rate (kbps) of the new meter
     * dscpIn - DSCP field that identifies the flow which is being applied the meter
     * portIn - Input port of the switch
     * portOut - Out port where flow packets are forwarding
     * precLevel - Increment of the DSCP field that is going to be applied to those packets which exceed the rate
     * 
     *  NOTE: If the user doesn't specify any "precLevel", then the flow packets which exceed
     *  the rate, instead of being remarked will be directly discarded
     */
    @Override
    public String setMeter(DatapathId dpid, int meterId, int meterRate, 
    					   int dscpIn, int portIn, int portOut, int precLevel){
    	
    	IOFSwitch sw = switchService.getSwitch(dpid);
    	
    	sendMeterMod(sw, meterId, meterRate, precLevel);
    	sendFlowMod(sw, dscpIn, portIn, portOut, meterId);
    	if(precLevel < 0){
    		return "Meter ID:" + meterId + " rate: " + meterRate + "kbps" +
    		       " DSCP_In: " + dscpIn + " portIn: " + portIn + " portOut: " + portOut + "action: DROP"
    		       + "\n" + "Flow and Meter entries have been installed successfully";
    	}else{
    		return "Meter ID:" + meterId + " rate: " + meterRate + "kbps" +
		       " DSCP_In: " + dscpIn + " portIn: " + portIn + " portOut: " + portOut + "action: REMARK DSCP"
		       + " precLevel: " + precLevel+ "\n" + "Flow and Meter entries have been installed successfully";
    	}
    }

    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		//Export interface IMetersService
        Collection<Class<? extends IFloodlightService>> l = 
        		new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IMetersService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		//Export interface IMetersService
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = 
        		new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IMetersService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
    	// register REST interface
        restApi.addRestletRoutable(new MetersWebRoutable());
    }
}

