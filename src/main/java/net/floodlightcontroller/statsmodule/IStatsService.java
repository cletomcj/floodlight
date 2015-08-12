package net.floodlightcontroller.statsmodule;

import java.util.ArrayList;
import java.util.Stack;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.NodePortTuple;

/**
 * 
 * Service exported by "StatsModule" module. Methods that are going to be called by the REST API
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public interface IStatsService extends IFloodlightService{

	//Method to obtain the current losses of the packet flow defined by IP addresses
	public String getLoss(IPv4Address srcIp, IPv4Address dstIp);
	
	//Method to activate the "automatic loss control" on the packet flow specified by IP addresses
	//loss - Loss threshold. When losses are greater than the threshold, the packet flow
	//       switch into an alternative route
	public String activaQoS(IPv4Address srcIp, IPv4Address dstIp, int loss);
	
	//Method to deactivate the "automatic loss control" on the packet flow specified by IP addresses
	public String desactivaQoS(IPv4Address srcIp, IPv4Address dstIp);
	
	//Method to obtain the delay between two switches
	public LinkDelay getDelay(DatapathId src, DatapathId dst);
	
	//Method to obtain the delay of the complete path of one packet flow
	public ArrayList<LinkDelay> getTotalDelay(IPv4Address srcIp, IPv4Address dstIp);
	
	//Method to obtain all possible paths between two nodes 
	public ArrayList<Stack<NodePortTuple>> getAllPaths(DatapathId src, DatapathId dst);
	

}

