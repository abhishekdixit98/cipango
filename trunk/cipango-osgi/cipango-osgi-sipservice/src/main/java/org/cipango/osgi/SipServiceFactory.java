package org.cipango.osgi;

import org.cipango.server.Server;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

public class SipServiceFactory implements ServiceFactory {

	private Server _server;

    public SipServiceFactory(Server server)
    {
    	_server = server;
    }

    public Object getService(Bundle bundle,
            ServiceRegistration registration)
    {
        Object srv = new SipServiceImpl(bundle, _server); 
        Activator.debug("** sip service get:" + bundle + ", service: " + srv);
        System.out.println(">>>>>>>>>Return " + srv);
        return srv;
    }

    public void ungetService(Bundle bundle,
            ServiceRegistration registration, Object service)
    {
    	Activator.debug("** sip service unget:" + bundle + ", service: " + service);
        ((SipServiceImpl) service).unregisterAll();
    }

}
