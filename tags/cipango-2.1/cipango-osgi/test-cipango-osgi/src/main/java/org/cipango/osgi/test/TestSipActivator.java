package org.cipango.osgi.test;

import org.cipango.osgi.api.SipService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

public class TestSipActivator implements BundleActivator, ServiceListener
{

	private BundleContext _bundleContext;
	private ServiceReference _ref;

	public void start(BundleContext bundleContext) throws Exception
	{
		_bundleContext = bundleContext;

		registerServlets();

		// Listener for SipService
		bundleContext.addServiceListener(this, "(objectClass=" + SipService.class.getName() + ")");

	}

	public void stop(BundleContext context) throws Exception
	{
		try
		{
			unregisterServlets();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	public void serviceChanged(ServiceEvent event)
	{
		try
		{
			switch (event.getType())
			{
			case ServiceEvent.REGISTERED:
				registerServlets();
				break;

			case ServiceEvent.UNREGISTERING:
				unregisterServlets();
				break;
			}
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
	}

	private void registerServlets()
	{
		try
		{
			if (_ref == null)
			{
				_ref = _bundleContext.getServiceReference(SipService.class.getName());
			}

			if (_ref != null)
			{
				SipService sipService = (SipService) _bundleContext.getService(_ref);
				
				sipService.registerServlet(new MainServlet());
				sipService.registerServlet(new Uas());
				sipService.registerServlet(new ProxyServlet());
				sipService.registerServlet(new B2bServlet());
			}
		}
		catch (Exception e)
		{
			System.err.println("Unable to register servlets");
			e.printStackTrace();
		}
	}


	private void unregisterServlets()
	{
		if (_ref != null)
		{
			SipService sipService = (SipService) _bundleContext.getService(_ref);
			sipService.unregister("MainServlet");
			sipService.unregister("uas");
			sipService.unregister("proxy");
			sipService.unregister("b2b");
			_bundleContext.ungetService(_ref);
			_ref = null;
		}
	}
}
