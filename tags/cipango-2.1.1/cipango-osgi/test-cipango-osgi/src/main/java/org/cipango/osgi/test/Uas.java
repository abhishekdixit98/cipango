package org.cipango.osgi.test;

import java.io.*;
import javax.servlet.*;
import javax.servlet.sip.*;

@javax.servlet.sip.annotation.SipServlet(name="uas")
public class Uas extends SipServlet {

	private static final long serialVersionUID = 8271049342551932569L;

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		log("UAS servlet: init");
	}

	protected void doRequest(SipServletRequest request) throws ServletException,
			IOException {
		log("UAS servlet: doRequest " + request.getMethod());
		
		if (!"ACK".equals(request.getMethod())) {
			SipServletResponse response = request.createResponse(200);
			response.setHeader("mode", "uas");
			response.setContent("Request treated by Cipango OSGi service".getBytes(),
					"text/plain");
			response.send();
			if (!"INVITE".equals(request.getMethod())) {
				request.getApplicationSession().invalidate();
			}
		}
	}
	

	public void destroy() {
		log("UAS servlet: destroy");
	}

}
