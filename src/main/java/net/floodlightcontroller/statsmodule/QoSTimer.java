package net.floodlightcontroller.statsmodule;

import java.util.TimerTask;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.IPv4Address;

/**
 * 
 * Auxiliary class that represents the TimerTask which is going to be executed periodically  
 * when losses automatic control is activated. It cotains all the fields necessary to measure
 * the losses and send out the FLOW_MOD messages 
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public abstract class QoSTimer extends TimerTask{
	
	//public QoSFlow qFlow;
	public IPv4Address srcIp;
	public IPv4Address dstIp;
	public Match match1;
	public Match match2;
	public IOFSwitch sw1;
	public IOFSwitch sw2;
	public int loss;
	
	public QoSTimer(IPv4Address srcIp, IPv4Address dstIp, Match m1, Match m2, IOFSwitch sw1, IOFSwitch sw2, int loss){
		super();
		//this.qFlow = qFlow;
		this.srcIp = srcIp;
		this.dstIp = dstIp;
		this.match1 = m1;
		this.match2 = m2;
		this.sw1 = sw1;
		this.sw2 = sw2;
		this.loss = loss;
	}
	
}
