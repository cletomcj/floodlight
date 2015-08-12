package net.floodlightcontroller.statsmodule;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;


/**
 * 
 * Class that forwards HTTP requests to "StatsResource" class
 * @author Carlos Martin-Cleto Jimenez
 *
 */
public class StatsWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/{op}/json", StatsResource.class);
        return router;
	}

	@Override
	public String basePath() {
        return "/wm/stats";
	}

}
