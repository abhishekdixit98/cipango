package org.cipango.osgi.test;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

@javax.servlet.sip.annotation.SipServlet(name="proxy")
public class ProxyServlet extends SipServlet
{

	protected void doRequest(SipServletRequest req) throws ServletException, IOException
	{
		if (req.isInitial())
		{
			Proxy proxy = req.getProxy();
			proxy.setRecordRoute(true);
			proxy.setSupervised(true);

			proxy.proxyTo(req.getRequestURI());
		}
	}

	protected void doResponse(SipServletResponse response)
	{
		response.setHeader("mode", "proxy");
		if (response.getMethod().equals("BYE"))
		{
			response.getApplicationSession().invalidate();
		}
	}
}
