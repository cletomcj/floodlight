package net.floodlightcontroller.metersmodule;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;


/**
 * 
 * This class forward HTTP POST requests to "MetersResource" class
 * 
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public class MetersWebRoutable implements RestletRoutable{
	
	@Override
	public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/add/json", MetersResource.class);
        return router;
	}

	@Override
	public String basePath() {
        return "/wm/meters";
	}

}
