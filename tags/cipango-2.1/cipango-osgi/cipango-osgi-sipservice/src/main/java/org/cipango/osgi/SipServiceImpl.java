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
package org.cipango.osgi;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServlet;

import org.cipango.osgi.api.SipService;
import org.cipango.server.Server;
import org.cipango.server.handler.SipContextHandlerCollection;
import org.cipango.servlet.SipServletHandler;
import org.cipango.servlet.SipServletHolder;
import org.cipango.sipapp.SipAppContext;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.webapp.Configuration;
import org.osgi.framework.Bundle;

public class SipServiceImpl implements SipService
{

	private SipContextHandlerCollection _contexts;

	/** Name of all servlet registered */
	private List<String> _servletNames = new ArrayList<String>();

	private SipAppContext _context;

	/** Bundle which "got" this service instance from the service factory */
	private Bundle _bundle = null;

	public SipServiceImpl(Bundle bundle, Server server)
	{
		_bundle = bundle;
		_contexts = (SipContextHandlerCollection) server.getHandler();
	}

	private void createContextHandler(ClassLoader loader, String name) throws ServletException
	{
		_context = new SipAppContext();
		_context.setServletHandler(new SipServletHandler());
		_context.getSipServletHandler().setStartWithUnavailable(false);
		_context.setConfigurations(new Configuration[] {});
		_context.setClassLoader(loader);
		_context.setProxyTimeout(180);
		_context.setSessionTimeout(12);
		_context.setContextPath(URIUtil.SLASH);
		_context.setName(name);
		_contexts.addHandler(_context);
	}

	public void unregister(String servletName)
	{
		_servletNames.remove(servletName);
		removeServlet(servletName, false);
	}
	
	public void unregister(SipServlet servlet)
	{
		SipServletHolder[] holders = _context.getSipServletHandler().getSipServlets();
		SipServletHolder holder = null;
		try
		{
			for (SipServletHolder servletHolder : holders)
			{
				if (servletHolder.getServlet() == servlet)
					holder = servletHolder;
			}
		}
		catch (ServletException ignore) 
		{
		}
		if (holder != null)
			removeServlet(holder.getName(), false);
	}

	public synchronized void unregisterAll()
	{
		Iterator<String> it = _servletNames.iterator();
		while (it.hasNext())
		{
			String name = it.next();
			it.remove();
			removeServlet(name, true);
		}
	}

	private void removeServlet(String servletName, boolean destroy)
	{
		Activator.debug("Sip unregister servlet :" + _bundle + ", name: " + servletName);

		if (_context == null)
			return;

		SipServletHolder holder = _context.getSipServletHandler().removeSipServlet(servletName);

		if (destroy)
		{
			try
			{
				holder.getServlet().destroy();
			}
			catch (Exception e)
			{
				Activator.warn("Unable to stop servlet " + servletName, e);
			}
		}

		if (_servletNames.size() == 0)
		{
			_contexts.removeHandler(_context);
			_context = null;
		}
	}

	public void registerListener(EventListener l) throws ServletException
	{
		if (_context == null)
			createContextHandler(l.getClass().getClassLoader(), _bundle.getSymbolicName());

		_context.addEventListener(l);
	}

	public void registerServlet(SipServlet servlet, Dictionary<String, String> initparams)
			throws ServletException
	{
		try
		{
			SipServletHolder holder = new SipServletHolder();
			holder.setServlet(servlet);
			javax.servlet.sip.annotation.SipServlet annotation = 
				servlet.getClass().getAnnotation(javax.servlet.sip.annotation.SipServlet.class);
			if (annotation == null)
			{
				holder.setName(servlet.getClass().getSimpleName());
				holder.setInitOrder(0); // Load on startup
			}
			else
			{
				holder.setName(annotation.name());
				holder.setInitOrder(annotation.loadOnStartup());
				holder.setDisplayName(annotation.description());
			}
					
			if (initparams != null)
			{
				Enumeration<String> e = initparams.keys();
				while (e.hasMoreElements())
				{
					String key = e.nextElement();
					holder.setInitParameter(key, initparams.get(key));
				}
			}

			if (_context == null)
			{
				String name = _bundle.getSymbolicName();
				if (annotation != null && annotation.applicationName() != null)
					name = annotation.applicationName();
				
				createContextHandler(servlet.getClass().getClassLoader(), name);
			}

			
			_context.getSipServletHandler().addSipServlet(holder);
			
			if (_context.getSipServletHandler().getMainServlet() == null)
				_context.getSipServletHandler().setMainServletName(holder.getName());

			Activator.info("Register servlet " + holder.getName() + " on context " + _context);

			if (!_context.isStarted())
			{
				try
				{
					_context.start();
					Activator.info("Start context " + _context);
				}
				catch (Exception e)
				{
					// make sure we unwind the adding process
					_contexts.removeHandler(_context);
					_context = null;
					throw new ServletException("Unable to create context handler", e);
				}
			}

			holder.start();

			_servletNames.add(holder.getName());
		}
		catch (ServletException e) {
			throw e;
		}
		catch (Exception e)
		{
			if (e.getCause() instanceof ServletException)
				throw (ServletException) e.getCause();
			throw new ServletException(e);
		}
	}

	public void registerServlet(SipServlet servlet) throws ServletException
	{
		registerServlet(servlet, null);
	}



}
