package net.floodlightcontroller.statsmodule;

import java.util.ArrayList;

import org.projectfloodlight.openflow.types.IPv4Address;

/**
 * 
 * Auxiliary class that represents a packet flow defined by IP addresses, the current
 * path of the packet flow and a list of all possible paths between the source switch and the destination switch
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public class QoSFlow {
	
	private IPv4Address srcIp;
	private IPv4Address dstIp;
	private FlowRoute rutaActual; //current path of the packet flow
	private ArrayList<FlowRoute> routes; //all possible paths 
	
	public QoSFlow(IPv4Address srcIp, IPv4Address dstIp, FlowRoute rutaActual, ArrayList<FlowRoute> routes) {
		this.srcIp = srcIp;
		this.dstIp = dstIp;
		this.rutaActual = rutaActual;
		this.routes = routes;
	}
	
	//This is the builder that is going to be used
	public QoSFlow(IPv4Address srcIp, IPv4Address dstIp){
		this.srcIp = srcIp;
		this.dstIp = dstIp;
		this.rutaActual = new FlowRoute();
		this.routes = new ArrayList<FlowRoute>();
		
	}

	public IPv4Address getSrcIp() {
		return srcIp;
	}

	public void setSrcIp(IPv4Address srcIp) {
		this.srcIp = srcIp;
	}

	public IPv4Address getDstIp() {
		return dstIp;
	}

	public void setDstIp(IPv4Address dstIp) {
		this.dstIp = dstIp;
	}
	
	public void setRutaActual(FlowRoute rutaActual){
		this.rutaActual = rutaActual;
	}
	
	public FlowRoute getRutaActual(){
		return rutaActual;
	}

	public ArrayList<FlowRoute> getRoutes() {
		return routes;
	}

	public void setRoutes(ArrayList<FlowRoute> routes) {
		this.routes = routes;
	}
	
	public void addFlowRoute(FlowRoute fr){
		this.routes.add(fr);
	}
	
	public String getKey(){
		return this.srcIp.toString()+this.dstIp.toString();
	}
	
	/*
	 * Method that sets the current path as a "path with losses"
	 */
	protected void setRutaActualLoss(){
		for(FlowRoute r:this.routes){
			if(r.equals(this.rutaActual)){
				r.setHaveLoss(true);
			}
		}
	}
	
	
	/*
	 * 
	 * Auxiliary method that finds a new available path without losses and sets it as the new current path. 
	 * And also updates the list of all possible paths setting the old path with losses
	 * 
	 */
	public FlowRoute changeRoute(){
		this.setRutaActualLoss();
		//Set as current path the next path available without losses
		for(FlowRoute r:this.routes){
			if(!r.isHaveLoss()){
				this.rutaActual = r;
				return r;
			}
		}
		//If any else path is found, returns the current path
		return this.rutaActual;
	}
}
