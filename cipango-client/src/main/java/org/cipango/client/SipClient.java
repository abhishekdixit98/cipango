// ========================================================================
// Copyright 2011 NEXCOM Systems
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.cipango.client;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.SipApplicationRouterInfo;
import javax.servlet.sip.ar.SipApplicationRoutingDirective;
import javax.servlet.sip.ar.SipApplicationRoutingRegion;
import javax.servlet.sip.ar.SipRouteModifier;
import javax.servlet.sip.ar.SipTargetedRequestInfo;

import org.cipango.server.Server;
import org.cipango.server.bio.UdpConnector;
import org.cipango.server.handler.SipContextHandlerCollection;
import org.cipango.servlet.SipServletHolder;
import org.cipango.sip.NameAddr;
import org.cipango.sipapp.SipAppContext;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

public class SipClient extends AbstractLifeCycle
{
	private Server _server;
	private SipAppContext _context;
	
	private List<UserAgent> _userAgents = new ArrayList<UserAgent>();
		
	public SipClient(String host, int port)
	{
		_server = new Server();
		
		UdpConnector connector = new UdpConnector();
		connector.setHost(host);
		connector.setPort(port);
		
		_server.getConnectorManager().addConnector(connector);
		_server.setApplicationRouter(new ApplicationRouter());
		
		SipContextHandlerCollection handler = new SipContextHandlerCollection();
		_server.setHandler(handler);
		
		_context = new SipAppContext();
		_context.setConfigurationClasses(new String[0]);
		_context.setContextPath("/");
		_context.setName(SipClient.class.getName());
		
		SipServletHolder holder = new SipServletHolder();
		holder.setServlet(new ClientServlet());
		holder.setName(ClientServlet.class.getName());
		
		_context.getSipServletHandler().addSipServlet(holder);
		_context.getSipServletHandler().setMainServletName(ClientServlet.class.getName());
		
		handler.addHandler(_context);
	}
	
	public SipClient(int port)
	{
		this(null, port);
	}
	
	public SipFactory getFactory()
	{
		return _context.getSipFactory();
	}
	
	public UserAgent createUserAgent(SipProfile profile)
	{
		UserAgent agent = new UserAgent(profile);
		addAgent(agent);
		return agent;
	}
	
	@Override
	protected void doStart() throws Exception
	{
		_server.start();
	}
	
	@Override
	protected void doStop() throws Exception
	{
		_server.stop();
	}
	
	public SipURI getContact()
	{
		return _server.getConnectorManager().getDefaultConnector().getSipUri();
	}
	
	public UserAgent getUserAgent(URI uri)
	{
		synchronized (_userAgents)
		{
			for (UserAgent agent : _userAgents)
			{
				if (agent.getProfile().getURI().equals(uri))
					return agent;
			}
		}
		return null;
	}
	
	public void addAgent(UserAgent agent)
	{
		SipURI contact = (SipURI) getContact().clone();
		
		agent.setFactory(_context.getSipFactory());
		agent.setContact(new NameAddr(contact));
		
		synchronized(_userAgents)
		{
			_userAgents.add(agent);
		}
	}
	
	@SuppressWarnings("serial")
	class ClientServlet extends SipServlet
	{
		protected MessageHandler getHandler(SipServletMessage message)
		{
			return (MessageHandler) message.getSession().getAttribute(MessageHandler.class.getName());
		}
		
		@Override
		protected void doRequest(SipServletRequest request) throws ServletException, IOException
		{
			MessageHandler handler = getHandler(request);
			if (handler != null)
				handler.handleRequest(request);
			
			if (request.isInitial())
			{
				Address local = request.getTo();
				UserAgent agent = getUserAgent(local.getURI());
			
				if (agent != null)
					agent.handleInitialRequest(request);
				else
					log("No agent for initial request: " + request.getMethod() + " " + request.getRequestURI());
			}
			else
			{
				log("No handler for request: " + request.getMethod() + " " + request.getRequestURI());
			}
		}
		
		@Override
		protected void doResponse(SipServletResponse response) throws ServletException, IOException
		{
			MessageHandler handler = getHandler(response);
			if (handler != null)
				handler.handleResponse(response);
			else
				log("No handler for response: " + response.getStatus() + " " + response.getMethod());
		}
	}
	
	class ApplicationRouter implements SipApplicationRouter
	{
		public void init() { }
		
		public void init(Properties properties) { }
		
		public void destroy() { }
		
		public void applicationDeployed(List<String> deployedApplications) { }
		
		public void applicationUndeployed(List<String> undeployedApplications) { }

		public SipApplicationRouterInfo getNextApplication(SipServletRequest request,
				SipApplicationRoutingRegion region, SipApplicationRoutingDirective directive,
				SipTargetedRequestInfo requestedInfo, Serializable info)
		{
			if (request.getRemoteAddr() == null)
				return null;
			return new SipApplicationRouterInfo(SipClient.class.getName(),
					SipApplicationRoutingRegion.NEUTRAL_REGION, 
					request.getFrom().getURI().toString(), 
					null,
					SipRouteModifier.NO_ROUTE, 
					1);
		}
	}
}
