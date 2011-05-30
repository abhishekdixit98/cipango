// ========================================================================
// Copyright 2008-2009 NEXCOM Systems
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

package org.cipango.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.SipApplicationRouterInfo;

import org.cipango.log.event.Events;
import org.cipango.server.ar.ApplicationRouterLoader;
import org.cipango.server.ar.RouterInfoUtil;
import org.cipango.server.handler.SipContextHandlerCollection;
import org.cipango.server.session.SessionManager;
import org.cipango.server.transaction.TransactionManager;
import org.cipango.sip.SipURIImpl;
import org.cipango.sipapp.SipAppContext;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

/**
 * Cipango SIP/HTTP Server.
 * It extends Jetty HTTP {@link org.eclipse.jetty.server.Server} to add SIP capabilities.
 */
public class Server extends org.eclipse.jetty.server.Server implements SipHandler
{
	private static final String __sipVersion;
	
	static 
	{
		if (Server.class.getPackage() != null && Server.class.getPackage().getImplementationVersion() != null)
			__sipVersion = Server.class.getPackage().getImplementationVersion();
		else
			__sipVersion = System.getProperty("cipango.version", "2.x.y-SNAPSHOT");
	}
    	
	private ThreadPool _sipThreadPool;
    
    private ConnectorManager _connectorManager = new ConnectorManager();
    private TransactionManager _transactionManager = new TransactionManager();
    
    private SessionManager _sessionManager;    
    private SipApplicationRouter _applicationRouter;

    private final AtomicLong _statsStartedAt = new AtomicLong(System.currentTimeMillis());
    
    public Server()
    {
    	setConnectorManager(_connectorManager);
		setTransactionManager(_transactionManager);
    }
	
	@Override
	protected void doStart() throws Exception 
    {
		Log.info("cipango-" + __sipVersion);

		MultiException mex = new MultiException();
		
		if (_sipThreadPool == null) 
			setSipThreadPool(new QueuedThreadPool());
		
		if (_sessionManager == null)
			setSessionManager(new SessionManager());
		
		try
		{
			super.doStart();
		}
		catch (Throwable t) { mex.add(t); }

		if (_applicationRouter == null)
			setApplicationRouter(ApplicationRouterLoader.loadApplicationRouter());

		try 
		{
			_applicationRouter.init();
			
			List<String> appNames = new ArrayList<String>();
			SipAppContext[] contexts = ((SipContextHandlerCollection) getHandler()).getSipContexts();
			if (contexts != null)
			{
				for (SipAppContext context : contexts)
					if (context.hasSipServlets() && context.isAvailable())
						appNames.add(context.getName());
			}
			_applicationRouter.applicationDeployed(appNames);
			
			_sessionManager.start();
			_connectorManager.start();
			
			if (contexts != null)
			{
				for (SipAppContext context : contexts)
					context.serverStarted();
			}
		}
		catch (Throwable t) { mex.add(t); }
		
		mex.ifExceptionThrow();
		
		Events.fire(Events.START, "Cipango " + __sipVersion + " started");
	}
	
	@Override
    protected void doStop() throws Exception
    {
		Events.fire(Events.STOP, "Stopping Cipango " + __sipVersion);
		
    	MultiException mex = new MultiException();
        
        try 
        {
        	_applicationRouter.destroy();
        	_connectorManager.stop();
    		_sessionManager.stop();
		} 
        catch (Throwable e) { mex.add(e); }

        try 
        {
			super.doStop();
		} 
        catch (Throwable e) { mex.add(e); }
               
        mex.ifExceptionThrow();
    }
    

	public void setApplicationRouter(SipApplicationRouter applicationRouter)
	{
		getContainer().update(this, _applicationRouter, applicationRouter, "applicationRouter");
		_applicationRouter = applicationRouter;
	}
	
	public SipApplicationRouter getApplicationRouter()
	{
		return _applicationRouter;
	}
	
    public void applicationStarted(SipAppContext context)
    {
    	if (isStarted())
    	{
    		_applicationRouter.applicationDeployed(Collections.singletonList(context.getName()));
    		context.serverStarted();
    	}
    }
    
    public void applicationStopped(SipAppContext context)
    {
    	if (isStarted())
    		_applicationRouter.applicationUndeployed(Collections.singletonList(context.getName()));
    }
    
    public void customizeRequest(SipRequest request) throws IOException
    {
    	if (!request.isInitial())
    		return;
    	
    	SipApplicationRouterInfo routerInfo = _applicationRouter.getNextApplication(
    			request,
    			request.getRegion(),
    			request.getRoutingDirective(),
    			null,
    			request.getStateInfo());
    	
    	if (routerInfo != null && routerInfo.getNextApplicationName() != null)
    	{
    		SipConnector connector = _connectorManager.getDefaultConnector();
    		SipURI route = new SipURIImpl(null, connector.getHost(), connector.getPort());
    		RouterInfoUtil.encode(route, routerInfo);
    		route.setLrParam(true);
    		
    		request.pushRoute(route);
    	}
    }
    
    /*
    public ClientTransaction sendRequest(SipRequest request, ClientTransactionListener listener) 
    {
    	if (!request.isInitial())
    	{
    		return request.session().sendRequest(request, listener);
    	}
    	else 
    	{
    		SipApplicationRouterInfo routerInfo = null;
    		try
    		{
	    		routerInfo = _applicationRouter.getNextApplication(
	    				request,
	    				request.getRegion(),
	    				request.getRoutingDirective(),
	    				null,
	    				request.getStateInfo());
	    		
	    		if (routerInfo != null && routerInfo.getNextApplicationName() != null)
	    		{
	    			SipConnector defaultConnector = _connectorManager.getDefaultConnector();
	    			SipURI internalRoute = new SipURIImpl(null, defaultConnector.getHost(), defaultConnector.getPort());
	    			RouterInfoUtil.encode(internalRoute, routerInfo);

	    			internalRoute.setLrParam(true);
	    			request.pushRoute(internalRoute);
	    		}
    		}
			catch (Throwable t) 
			{
				// TODO, send 500 sync/async ?
				Log.warn(t);
			}
			return request.session().sendRequest(request, listener);
       	}
    }
    */
	
    public void handle(SipServletMessage message) throws IOException, ServletException
    {
		((SipHandler) getHandler()).handle(message); 
	}
	
	public void setTransactionManager(TransactionManager transactionManager) 
	{
		getContainer().update(this, _transactionManager, transactionManager, "transactionManager", true);
		_transactionManager = transactionManager;
		_transactionManager.setServer(this);
	}
	
	public void setConnectorManager(ConnectorManager connectorManager)
	{
		getContainer().update(this, _connectorManager, connectorManager, "connectorManager", true);
		_connectorManager = connectorManager;
		_connectorManager.setServer(this);
	}
	
	public void setSipThreadPool(ThreadPool sipThreadPool) 
	{
		if (_sipThreadPool!=null)
            removeBean(_sipThreadPool);
        getContainer().update(this, _sipThreadPool, sipThreadPool, "sipThreadPool",false);
        _sipThreadPool = sipThreadPool;
        if (_sipThreadPool !=null)
            addBean(_sipThreadPool);
	}
	
	public void setSessionManager(SessionManager sessionManager) 
	{
		getContainer().update(this, _sessionManager, sessionManager, "sessionManager", true);
		_sessionManager = sessionManager;
		_sessionManager.setServer(this);
	}
	
	public ThreadPool getSipThreadPool()
	{
		return _sipThreadPool;
	}
	
	public ConnectorManager getConnectorManager()
	{
		return _connectorManager;
	}
	
	public TransactionManager getTransactionManager()
	{
		return _transactionManager;
	}
	
	public SessionManager getSessionManager()
	{
		return _sessionManager;
	}
	
	public void allStatsReset()
	{
		_statsStartedAt.set(System.currentTimeMillis());
		getSessionManager().statsReset();
		getConnectorManager().statsReset();
		getTransactionManager().statsReset();
		if (_handler instanceof HandlerCollection)
		{
			Handler[] handlers = ((HandlerCollection) _handler).getChildHandlersByClass(SipAppContext.class);
			if (handlers != null)
			{
				for (Handler handler : handlers)
					((SipAppContext) handler).statsReset();
			}
		}
	}
	
	public long getStatsStartedAt()
	{
		return _statsStartedAt.get();
	}
	
	public static String getSipVersion()
	{
		return __sipVersion;
	}
	
    @Override
    public void dump(Appendable out,String indent) throws IOException
    {
        super.dump(out, indent);
        dump(out,indent,TypeUtil.asList(_connectorManager.getConnectors()), Arrays.asList(_applicationRouter));    
    }
	
	@Override
	public String toString()
	{
		return "cipango-" + __sipVersion;
	}
}
