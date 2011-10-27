/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.cipango.osgi;

import org.cipango.dar.DefaultApplicationRouter;
import org.cipango.osgi.api.SipService;
import org.cipango.server.Server;
import org.cipango.server.bio.TcpConnector;
import org.cipango.server.bio.UdpConnector;
import org.cipango.server.handler.SipContextHandlerCollection;
import org.eclipse.jetty.util.log.Log;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceRegistration;

/**
 *  Basic implementation of OSGi SIP service.
 *
 *  TODO:
 *
 *      - fuller suite of testing and compatibility tests
 *
 *      - only exposed params are those defined in the OSGi spec. Jetty is
 *        very tunable via params, some of which it may be useful to expose
 *
 *      - no cacheing is performed on delivered resources. Although not part
 *        of the OSGi spec, it also isn't precluded and would enhance
 *        performance in a high usage environment. Jetty's ResourceHandler
 *        class could be a model for this.
 *
 *      - scanning the Jetty ResourceHandler class it's clear that there are
 *        many other sophisticated areas to do with resource handling such
 *        as checking date and range fields in the http headers. It's not clear
 *        whether any of these play a part in the OSGi service - the spec
 *        just describes "returning the contents of the URL to the client" which
 *        doesn't state what other HTTP handling might be compliant or desirable
 */
public class Activator implements BundleActivator
{
    protected static boolean __debug = false;

    private BundleContext _bundleContext = null;
    private ServiceRegistration _sipServiceRegistration;
    private Server _server = null;

    public void start(BundleContext bundleContext)
        throws BundleException
    {
        _bundleContext = bundleContext;

        // org.mortbay.util.Loader needs this (used for JDK 1.4 log classes)
        Thread.currentThread().setContextClassLoader(
                this.getClass().getClassLoader());
        
        String optDebug =
            _bundleContext.getProperty("org.cipango.debug");
        if (optDebug != null && optDebug.toLowerCase().equals("true"))
        {
            Log.getLog().setDebugEnabled(true);
            __debug = true;
        }

       
        try
        {
            initializeServer();
        } catch (Exception e) {
            //TODO: maybe throw a bundle exception in here?
            warn("Unable to initialize Cipango", e);
            return;
        }
        
        SipServiceFactory sipServiceFactory = new SipServiceFactory(_server);
        _sipServiceRegistration = _bundleContext.registerService(
        		SipService.class.getName(), sipServiceFactory, null);
    }

    
    private int getPort(String property, int defaultValue) {
    	int port;
    	try
        {
            port = Integer.parseInt(_bundleContext.getProperty(property));
        }
        catch (Exception e)
        {
        	info("Unable to get property " + property  
        			+ ";use default: " + defaultValue);
            port = defaultValue;
        }
        return port;
    }
    
    
    public void stop(BundleContext bundleContext)
        throws BundleException
    {
        //TODO: wonder if we need to closedown service factory ???
        
        if (_sipServiceRegistration != null) 
        {
        	_sipServiceRegistration.unregister();
        }

        try
        {
            _server.stop();
        }
        catch (Exception e)
        {
            warn("Unable to stop Cipango server", e);
        }
    }

    protected void initializeServer()
        throws Exception
    {

        // Create server
        _server = new Server();
        
        UdpConnector udp = new UdpConnector();
        TcpConnector tcp = new TcpConnector();
        String host = _bundleContext.getProperty(SipService.SIP_HOST);
        int sipPort = getPort(SipService.SIP_PORT, 5060);
        udp.setHost(host);tcp.setHost(host);
        udp.setPort(sipPort); tcp.setPort(sipPort);
       
        _server.getConnectorManager().addConnector(udp);
        _server.getConnectorManager().addConnector(tcp);
        
        _server.setHandler(new SipContextHandlerCollection());
        _server.setApplicationRouter(new DefaultApplicationRouter());
        
        _server.start();
    }
    
    public static void debug(String txt)
    {
        if (__debug)
        {
            System.err.println(">>Felix Cipango: [DEBUG] " + txt);
        }
    }    
    
    public static void info(String txt)
    {
        System.err.println(">>Felix Cipango: [INFO] " + txt);
    } 
    
    public static void warn(String reason)
    {
        System.err.println(">>Felix Cipango: [WARN] " + reason);
    } 
    
    public static void warn(String reason, Throwable e)
    {
        System.err.println(">>Felix Cipango: [WARN] " + reason);
        e.printStackTrace();
    } 
}