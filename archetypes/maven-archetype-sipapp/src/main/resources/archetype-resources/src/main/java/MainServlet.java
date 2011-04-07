package $package;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class MainServlet extends javax.servlet.sip.SipServlet
{
	private String serverInfo;

	@Override
	public void init()
	{
		serverInfo = getServletContext().getServerInfo();
	}

	@Override
	protected void doOptions(SipServletRequest options) throws ServletException, IOException
	{
		SipServletResponse ok = options.createResponse(SipServletResponse.SC_OK);
		ok.addHeader("Server", serverInfo);
		ok.send();
	}
}
