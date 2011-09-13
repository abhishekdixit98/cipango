package org.cipango.osgi.test;
// ========================================================================
// Copyright (c) 2010 Intalio, Inc.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// Contributors:
//    Hugues Malphettes - initial API and implementation
// ========================================================================


import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.TimerListener;
import javax.servlet.sip.TimerService;

import org.cipango.client.MessageHandler;
import org.cipango.client.SipClient;
import org.cipango.client.SipProfile;
import org.cipango.client.UserAgent;
import org.cipango.osgi.api.SipService;
import org.cipango.sip.SipURIImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Inject;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.container.def.PaxRunnerOptions;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;


/**
 * Pax-Exam to make sure the jetty-osgi-boot can be started along with the httpservice web-bundle.
 * Then make sure we can deploy an OSGi service on the top of this.
 */
@RunWith( JUnit4TestRunner.class )
public class TestSipService
{
    /**
     * Jetty-osgi including webapp support and also jetty-client.
     * Sets the system property jetty.home.bunde=org.eclipse.jetty.osgi.boot
     * to use the jetty server configuration embedded in 
     * @return
     */
    public static List<Option> provisionCoreJetty()
    {
        return Arrays.asList(options(
                //get the jetty home config from the osgi boot bundle.
                PaxRunnerOptions.vmOptions("-Dsip.port=5058 -Dsip.host=localhost"),
                
        		mavenBundle().groupId( "org.mortbay.jetty" ).artifactId( "servlet-api" ).versionAsInProject().noStart(),
                mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-server" ).versionAsInProject().noStart(),   
                mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-servlet" ).versionAsInProject().noStart(),  
                mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-util" ).versionAsInProject().noStart(), 
                mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-http" ).versionAsInProject().noStart(), 
	            mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-xml" ).versionAsInProject().noStart(),  
	            mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-webapp" ).versionAsInProject().noStart(),
	            mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-io" ).versionAsInProject().noStart(),
	            mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-continuation" ).versionAsInProject().noStart(),
                mavenBundle().groupId( "org.eclipse.jetty" ).artifactId( "jetty-security" ).versionAsInProject().noStart()
        ));
        
    }
    
    public static List<Option> provisionCoreCipango()
    {
        return Arrays.asList(options(
              mavenBundle().groupId( "javax.servlet" ).artifactId( "sip-api" ).versionAsInProject().noStart(),
              mavenBundle().groupId( "org.cipango" ).artifactId( "cipango-server" ).versionAsInProject().noStart(),
              mavenBundle().groupId( "org.cipango" ).artifactId( "cipango-client" ).versionAsInProject().noStart(),
              mavenBundle().groupId( "org.cipango" ).artifactId( "cipango-dar" ).versionAsInProject().noStart()
         ));
        
    }
    
    @Inject
    BundleContext bundleContext = null;


    @Configuration
    public static Option[] configure()
    {
        ArrayList<Option> options = new ArrayList<Option>();
        options.addAll(provisionCoreJetty());
        options.addAll(provisionCoreCipango());
        options.addAll(Arrays.asList(options(
        // install log service using pax runners profile abstraction (there are more profiles, like DS)
        //logProfile(),
        // this is how you set the default log level when using pax logging (logProfile)
        //systemProperty( "org.ops4j.pax.logging.DefaultServiceLog.level" ).value( "INFO" ),
                		
        mavenBundle().groupId( "org.cipango.osgi" ).artifactId( "cipango-osgi-sipservice" ).versionAsInProject().start(),
        mavenBundle().groupId( "org.cipango.osgi" ).artifactId( "osgi-sipservice" ).versionAsInProject().start()
            
         )));
        return options.toArray(new Option[options.size()]);
    }


    @Test
    public void testSipServlet() throws Exception
    {
    	
        Map<String,Bundle> bundlesIndexedBySymbolicName = new HashMap<String, Bundle>();
        for( Bundle b : bundleContext.getBundles() )
            bundlesIndexedBySymbolicName.put(b.getSymbolicName(), b);

        Bundle bundle = bundlesIndexedBySymbolicName.get("org.cipango.osgi.sipservice");
        Assert.assertNotNull("Could not find the cipango-osgi-sipservice", bundle);
        Assert.assertTrue(bundle.getState() == Bundle.ACTIVE);

        SipService sipService = getSipService();
        Assert.assertNotNull(sipService);
        
        SipServlet servlet = new SipServlet()
        {

			@Override
			protected void doRequest(SipServletRequest request) throws ServletException, IOException
			{
				request.createResponse(SipServletResponse.SC_OK).send();
			}
        	
        };
        sipService.registerServlet(servlet);
        
        SipClient client = new SipClient("localhost", 5057);
        client.start();
        
        try
        {
	        SipFactory factory = client.getFactory();
	        SipServletRequest request = 
	        	factory.createRequest(factory.createApplicationSession(), "MESSAGE", "sip:test@cipango.org", "sip:osgi@localhost:5058");
	        
	        ResponseHandler handler = new ResponseHandler();
	        request.getSession().setAttribute(MessageHandler.class.getName(), handler);
	        request.send();
	        synchronized (handler)
			{
				if (handler.getResponse() == null)
					handler.wait(1000);
			}
	        
	        SipServletResponse response = handler.getResponse();
	        Assert.assertNotNull(response);
	        Assert.assertEquals(SipServletResponse.SC_OK, response.getStatus());
        }
        finally
        {
        	client.stop();
        }

    }
    
    
    @Test
    public void testListener() throws Exception
    {
        SipService sipService = getSipService();
        
        TestServlet servlet = new TestServlet();
        sipService.registerServlet(servlet);
        sipService.registerListener(servlet);
        
        SipClient client = new SipClient("localhost", 5057);
        client.start();
        
        try
        {
	        SipFactory factory = client.getFactory();
	        SipServletRequest request = 
	        	factory.createRequest(factory.createApplicationSession(), "MESSAGE", "sip:test@cipango.org", "sip:osgi@localhost:5058");
	        
	        ResponseHandler handler = new ResponseHandler();
	        request.getSession().setAttribute(MessageHandler.class.getName(), handler);
	        request.send();
	        synchronized (handler)
			{
	        	System.out.println(">>>>>>Wait");
	        	Thread.sleep(1500);
				if (handler.getResponse() == null)
					handler.wait(1000);
				System.out.println(">>>>>>Done");
			}
	        
	        SipServletResponse response = handler.getResponse();
	        Assert.assertNotNull(response);
	        Assert.assertEquals(SipServletResponse.SC_OK, response.getStatus());
	        Assert.assertNotNull(response.getHeader("Info"));
        }
        finally
        {
        	client.stop();
        }

    }
    
    private SipService getSipService()
    {
    	//in the OSGi world this would be bad code and we should use a bundle tracker.
        //here we purposely want to make sure that the sipService is actually ready.
    	ServiceReference sr  =  bundleContext.getServiceReference(SipService.class.getName());
        Assert.assertNotNull("The cipango-osgi-sip-service is started and should have deployed a service reference for HttpService" ,sr);
        return (SipService)bundleContext.getService(sr);
    }
    
    @Test
	public void testExceptionRegisterServlet() throws Exception
	{
		SipService sipService = getSipService();
		TestServlet testServlet = new TestServlet();

		sipService.registerServlet(testServlet);
		sipService.registerListener(testServlet);
		testServlet.launchTimer();
		try
		{
			sipService.registerServlet(new TestServlet());
			Assert.fail("Able to register with same name");
		}
		catch (ServletException e)
		{
			System.out.println("Success: unable to register with same name");
		}
		try
		{
			sipService.registerServlet(new BadInitServlet());
			Assert.fail("Servlet init does not throw exception");
		}
		catch (ServletException e)
		{
			System.out.println("Success: servlet init does throw exception");
		}
	}
    
	static class BadInitServlet extends SipServlet
	{
		public void init(ServletConfig config) throws ServletException
		{
			throw new ServletException("Bad init");
		}
	}
	
	static class TestServlet extends SipServlet implements TimerListener {

		private TimerService _timerService;
		
		public void destroy() {
			super.destroy();
		}

		public void init(ServletConfig config) throws ServletException {
			super.init(config);
			_timerService = (TimerService) getServletContext().getAttribute(TIMER_SERVICE);
		}
		
		@Override
		protected void doRequest(SipServletRequest request) throws ServletException, IOException
		{
			request.getApplicationSession().setAttribute(SipServletRequest.class.getName(), request);
			_timerService.createTimer(request.getApplicationSession(), 100, false, 
					"Timer set at " + new Date());
		}
		
		public void launchTimer() {
			
		}

		public void timeout(ServletTimer timer) 
		{
			try
			{
				System.out.println("Timer fire: " + timer.getInfo());
				SipServletRequest request = (SipServletRequest) timer.getApplicationSession().getAttribute(SipServletRequest.class.getName());
				SipServletResponse response = request.createResponse(SipServletResponse.SC_OK);
				response.setHeader("Info", timer.getInfo().toString());
				response.send();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}


		
	}

	
	static class ResponseHandler implements MessageHandler
	{
		private SipServletResponse _response;

		public void handleRequest(SipServletRequest request) throws IOException, ServletException
		{
		}

		public void handleResponse(SipServletResponse response) throws IOException, ServletException
		{
			
			System.out.println("Got response " + response);
			_response = response;
			synchronized (this)
			{
				notify();
			}
		}

		public SipServletResponse getResponse()
		{
			return _response;
		}
		
	}
    
}
