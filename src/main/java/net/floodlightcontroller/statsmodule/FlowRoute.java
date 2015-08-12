package net.floodlightcontroller.statsmodule;

import java.util.Stack;

import net.floodlightcontroller.topology.NodePortTuple;

/**
 * 
 * Auxiliary class to store the current path of packet flows and set if the packet flow has losses or not
 *
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public class FlowRoute {
	
	private Stack<NodePortTuple> path;
	private boolean haveLoss;
	
	public FlowRoute(Stack<NodePortTuple> path, boolean isWorking, boolean haveLoss){
		this.path = path;
		this.haveLoss = haveLoss;
	}
	
	public FlowRoute(){
		this.path = new Stack<NodePortTuple>();
		this.haveLoss = false;
	}

	public Stack<NodePortTuple> getPath() {
		return path;
	}

	public void setPath(Stack<NodePortTuple> path) {
		this.path = path;
	}
	
	
	public boolean isHaveLoss() {
		return haveLoss;
	}

	public void setHaveLoss(boolean haveLoss) {
		this.haveLoss = haveLoss;
	}
	
	 @Override
	    public boolean equals(Object obj) {
	        if (this == obj)
	            return true;
	        if (obj == null)
	            return false;
	        if (getClass() != obj.getClass())
	            return false;
	        FlowRoute other = (FlowRoute) obj;
	        return this.getPath().equals(other.getPath());
	    }
	

}
