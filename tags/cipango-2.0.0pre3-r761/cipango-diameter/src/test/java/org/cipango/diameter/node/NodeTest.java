package org.cipango.diameter.node;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import junit.framework.Assert;

import org.cipango.diameter.AVP;
import org.cipango.diameter.AVPList;
import org.cipango.diameter.api.DiameterFactory;
import org.cipango.diameter.api.DiameterServletAnswer;
import org.cipango.diameter.api.DiameterServletRequest;
import org.cipango.diameter.api.DiameterSession;
import org.cipango.diameter.base.Common;
import org.cipango.diameter.base.Common.AuthSessionState;
import org.cipango.diameter.ims.Cx;
import org.cipango.diameter.ims.Sh;
import org.cipango.diameter.ims.Sh.DataReference;

public class NodeTest extends TestCase
{
	private Node _client;
	private Node _server;
	private Peer _peer;

	public void setUp() throws Exception
	{
		_client = new Node(38681);
		_client.getConnectors()[0].setHost("127.0.0.1");
		_client.setIdentity("client");
		
		_peer = new Peer("server");
		_peer.setAddress(InetAddress.getByName("127.0.0.1"));
		_peer.setPort(38680);
		_client.addPeer(_peer);
		
		_server = new Node(38680);
		_server.getConnectors()[0].setHost("127.0.0.1");
		_server.setIdentity("server");
	}

	public void tearDown() throws Exception
	{
		_server.stop();
		_client.stop();
	}
	
	public void testConnect() throws Exception
	{
		//org.eclipse.jetty.util.log.Log.getLog().setDebugEnabled(true);
		
		_server.start();
		
		_client.start();
		
		waitPeerOpened();
		
		Peer clientPeer = _server.getPeer("client");
		assertNotNull(clientPeer);
		assertTrue(clientPeer.isOpen());
		
		_peer.stop();
		Thread.sleep(100);
		assertTrue(_peer.isClosed());
		assertTrue(clientPeer.isClosed());
	}
	
	public void testUdr() throws Throwable
	{
		//Log.getLog().setDebugEnabled(true);
		
		TestDiameterHandler serverHandler = new TestDiameterHandler()
		{

			@Override
			public void doHandle(DiameterMessage message) throws Throwable
			{
				DiameterServletAnswer uda;
				DiameterServletRequest request = (DiameterServletRequest) message;

				assertEquals(true, message.isRequest());
				assertEquals(Sh.UDR, request.getCommand());
				assertEquals(request.getApplicationId(), Sh.SH_APPLICATION_ID.getId());
				assertEquals(request.getDestinationHost(), "server");
				uda = request.createAnswer(Common.DIAMETER_SUCCESS);
				uda.send();
			}
			
		};
		_server.setHandler(serverHandler);
		_server.start();
		
		TestDiameterHandler clientHandler = new TestDiameterHandler()
		{
			
			@Override
			public void doHandle(DiameterMessage message) throws Throwable
			{
				DiameterServletAnswer uda = (DiameterServletAnswer) message;
	
				assertFalse(message.isRequest());
				assertEquals(Sh.UDA, uda.getCommand());
				assertEquals(Common.DIAMETER_SUCCESS, uda.getResultCode());
				assertEquals(uda.getApplicationId(), Sh.SH_APPLICATION_ID.getId());

			}
		};
		_client.setHandler(clientHandler);
		_client.start();
		
		waitPeerOpened();
				
		DiameterRequest udr = new DiameterRequest(_client, Sh.UDR, Sh.SH_APPLICATION_ID.getId(), _client.getSessionManager().newSessionId());
		udr.getAVPs().add(Common.DESTINATION_REALM, "server");
		udr.getAVPs().add(Common.DESTINATION_HOST, "server");
		udr.getAVPs().add(Sh.DATA_REFERENCE, DataReference.SCSCFName);
		AVP<AVPList> userIdentity = new AVP<AVPList>(Sh.USER_IDENTITY, new AVPList());
        userIdentity.getValue().add(Cx.PUBLIC_IDENTITY, "sip:alice@cipango.org");
		udr.getAVPs().add(userIdentity);
		udr.getAVPs().add(Common.AUTH_SESSION_STATE, AuthSessionState.NO_STATE_MAINTAINED);
		udr.getSession();
		udr.send();
		serverHandler.assertDone();
		clientHandler.assertDone();
	}
	
	public void testRedirect() throws Throwable
	{
		//Log.getLog().setDebugEnabled(true);
		Node server2 = null;
		try
		{
			TestDiameterHandler redirectHandler = new TestDiameterHandler()
			{
	
				@Override
				public void doHandle(DiameterMessage message) throws Throwable
				{
					DiameterServletAnswer uda;
					DiameterServletRequest request = (DiameterServletRequest) message;
	
					assertEquals(true, message.isRequest());
					assertEquals(Sh.UDR, request.getCommand());
					uda = request.createAnswer(Common.DIAMETER_REDIRECT_INDICATION);
					uda.add(Common.REDIRECT_HOST, "localhost");
					uda.add(Common.REDIRECT_HOST, "server3");
					uda.send();
				}
				
			};
			_server.setHandler(redirectHandler);
			_server.start();
			
			
			server2 = new Node(3868);
			server2.getConnectors()[0].setHost("127.0.0.1");
			server2.setIdentity("localhost");
			TestDiameterHandler serverHandler = new TestDiameterHandler()
			{
	
				@Override
				public void doHandle(DiameterMessage message) throws Throwable
				{
					DiameterServletAnswer uda;
					DiameterServletRequest request = (DiameterServletRequest) message;
	
					assertEquals(true, message.isRequest());
					assertEquals(Sh.UDR, request.getCommand());
					assertEquals(request.getApplicationId(), Sh.SH_APPLICATION_ID.getId());
					assertEquals(request.getDestinationHost(), "server");
					uda = request.createAnswer(Common.DIAMETER_SUCCESS);
					uda.send();
				}
				
			};
			server2.setHandler(serverHandler);
			server2.start();
			
			TestDiameterHandler clientHandler = new TestDiameterHandler()
			{
				
				@Override
				public void doHandle(DiameterMessage message) throws Throwable
				{
					DiameterServletAnswer uda = (DiameterServletAnswer) message;
		
					assertFalse(message.isRequest());
					assertEquals(Sh.UDA, uda.getCommand());
					assertEquals(Common.DIAMETER_SUCCESS, uda.getResultCode());
					assertEquals(uda.getApplicationId(), Sh.SH_APPLICATION_ID.getId());
	
				}
			};
			_client.setHandler(clientHandler);
			_client.start();
			
			waitPeerOpened();
					
			DiameterRequest udr = new DiameterRequest(_client, Sh.UDR, Sh.SH_APPLICATION_ID.getId(), _client.getSessionManager().newSessionId());
			udr.getAVPs().add(Common.DESTINATION_REALM, "server");
			udr.getAVPs().add(Common.DESTINATION_HOST, "server");
			udr.getAVPs().add(Sh.DATA_REFERENCE, DataReference.SCSCFName);
			AVP<AVPList> userIdentity = new AVP<AVPList>(Sh.USER_IDENTITY, new AVPList());
	        userIdentity.getValue().add(Cx.PUBLIC_IDENTITY, "sip:alice@cipango.org");
			udr.getAVPs().add(userIdentity);
			udr.getAVPs().add(Common.AUTH_SESSION_STATE, AuthSessionState.NO_STATE_MAINTAINED);
			udr.getSession();
			udr.send();
			redirectHandler.assertDone();
			clientHandler.assertDone();
		}
		finally
		{
			if (server2 != null)
				server2.stop();
		}
	}
	
	protected DiameterFactory createFactory(Node node)
	{
		DiameterFactoryImpl factory = new DiameterFactoryImpl();
		factory.setNode(node);
		return factory;
	}
	
	public void testDiameterFactory() throws Throwable
	{
		//Log.getLog().setDebugEnabled(true);
		
		TestDiameterHandler serverHandler = new TestDiameterHandler()
		{

			@Override
			public void doHandle(DiameterMessage message) throws Throwable
			{
				DiameterServletAnswer uda;
				DiameterServletRequest request = (DiameterServletRequest) message;

				assertEquals(true, message.isRequest());
				assertEquals(Sh.UDR, request.getCommand());
				assertEquals(request.getApplicationId(), Sh.SH_APPLICATION_ID.getId());
				assertEquals(request.getDestinationHost(), "server");
				uda = request.createAnswer(Common.DIAMETER_SUCCESS);
				uda.send();
			}
			
		};
		_server.setHandler(serverHandler);
		_server.start();
		
		TestDiameterHandler clientHandler = new TestDiameterHandler()
		{
			
			@Override
			public void doHandle(DiameterMessage message) throws Throwable
			{
				DiameterServletAnswer uda = (DiameterServletAnswer) message;
	
				assertFalse(message.isRequest());
				assertEquals(Sh.UDA, uda.getCommand());
				assertEquals(uda.getApplicationId(), Sh.SH_APPLICATION_ID.getId());

			}
		};
		_client.setHandler(clientHandler);
		_client.start();
		
		waitPeerOpened();
		

		DiameterFactory clientFactory = createFactory(_client);
		DiameterServletRequest udr = clientFactory.createRequest(null, Sh.SH_APPLICATION_ID, Sh.UDR, "server");
		
		udr.add(Common.DESTINATION_HOST, "server");
		udr.getAVPs().add(Sh.DATA_REFERENCE, DataReference.SCSCFName);
		AVP<AVPList> userIdentity = new AVP<AVPList>(Sh.USER_IDENTITY, new AVPList());
        userIdentity.getValue().add(Cx.PUBLIC_IDENTITY, "sip:alice@cipango.org");
		udr.getAVPs().add(userIdentity);
		udr.getAVPs().add(Common.AUTH_SESSION_STATE, AuthSessionState.NO_STATE_MAINTAINED);
		udr.getSession();
		udr.send();
		serverHandler.assertDone();
		clientHandler.assertDone();
	}
	
	private void waitPeerOpened()
	{
		int i = 50;
		while (i != 0)
		{
			if (_peer.isOpen())
				return;
			try { Thread.sleep(20); } catch (InterruptedException e) {}
			i++;
		}
		assertTrue(_peer.isOpen());
	}
	

		
	public static abstract class TestDiameterHandler implements DiameterHandler
	{
		private Throwable _e;
		private AtomicInteger _msgReceived = new AtomicInteger(0);
				
		public void handle(DiameterMessage message)
		{
			try
			{
				doHandle(message);
			}
			catch (Throwable e)
			{
				e.printStackTrace();
				_e = e;
			}
			finally
			{
				_msgReceived.incrementAndGet();
				synchronized (_msgReceived)
				{
					_msgReceived.notify();
				}
			}
		}
		
		public abstract void doHandle(DiameterMessage message) throws Throwable;
		
		
		public void assertDone() throws Throwable
		{
			assertDone(1);
		}
		
		public void assertDone(int msgExpected) throws Throwable
		{
			if (_e != null)
				throw _e;
			
			long end = System.currentTimeMillis() + 5000;
			
			synchronized (_msgReceived)
			{
				while (end > System.currentTimeMillis() && _msgReceived.get() < msgExpected)
				{
					try
					{
						_msgReceived.wait(end - System.currentTimeMillis());
					}
					catch (InterruptedException e)
					{
					}
				}
			}
			if (_e != null)
				throw _e;
			if (_msgReceived.get() != msgExpected)
				Assert.fail("Received " + _msgReceived + " messages when expected " + msgExpected);
		}
	}
	
}
