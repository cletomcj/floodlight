package net.floodlightcontroller.metersmodule;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * 
 * Service exported by MetersModule module. This method will be called from REST API
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public interface IMetersService extends IFloodlightService {
	
	public String setMeter(DatapathId dpid, int meterId, int meterRate, int dscpIn, int portIn, int portOut, int precLev);

}
