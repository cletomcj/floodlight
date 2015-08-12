package net.floodlightcontroller.statsmodule;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.routing.Link;


/**
 * 
 * 
 * Auxiliary class that represents a physichal link between two switches with the associated delay
 * in nanoseconds. 
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */

public class LinkDelay extends Link{
	
	private long delay; //Link delay (nanoseconds)
	
	public LinkDelay(long delay, DatapathId srcId, OFPort srcPort, DatapathId dstId, OFPort dstPort){
		super(srcId,srcPort,dstId,dstPort);
		this.delay = delay;
	}
	
	public LinkDelay(Link l, long delay){
		super(l.getSrc(),l.getSrcPort(),l.getDst(),l.getDstPort());
		this.delay = delay;
	}
	
	public void setDelay(long d){
		this.delay = d;
	}
	
	public long getDelay(){
		return this.delay;
	}
	
}
