package org.cipango.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import junit.framework.Assert;

import org.cipango.client.MessageHandler;
import org.cipango.client.SipClient;
import org.cipango.client.SipProfile;
import org.cipango.client.UserAgent;
import org.cipango.server.AbstractSipConnector.EventHandler;
import org.cipango.server.ID;
import org.cipango.server.SipMessage;
import org.cipango.server.SipRequest;
import org.cipango.server.SipResponse;
import org.cipango.sip.SipFields;
import org.cipango.sip.SipHeaders;
import org.cipango.sip.SipMethods;
import org.cipango.sip.SipParser;
import org.cipango.sip.Via;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @version $Revision$ $Date$
 */
public class WebSocketTest
{

	public static final String UAS = "uas";
	public static final String PROXY = "proxy";
	
    private static WebSocketClientFactory _factory = new WebSocketClientFactory();
    private static final String BASE_URL = "ws://127.0.0.1:8078/test-cipango-websocket/";
    private static int __port;
    private WebSocketClient _client;
    private TestWebSocket _websocket;
    
    
    @BeforeClass
    public static void startServer() throws Exception
    {
        _factory.start();
        __port = Math.abs(new Random().nextInt() % 30000) + 20000;
    }

    @AfterClass
    public static void stopServer() throws Exception
    {
        _factory.stop();
    }
    
    @Before
    public void setUp() throws Exception
    {
    	_client = new WebSocketClient(_factory);
    	__port++;
    	_client.setBindAddress(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), __port));
    	_websocket = new TestWebSocket();
    	_client.open(new URI(BASE_URL), _websocket);
    	waitWsEvent();
    	Assert.assertTrue("Websocket is not open", _websocket.isOpen());
    	
    }
    
    private void waitWsEvent() throws InterruptedException
    {
    	synchronized (_websocket)
		{
    		_websocket.wait(2000);
		}
    }

    @Test
    public void testSimpleMessage() throws Exception
    {
    	_websocket.getConnection().sendMessage(getRawMessage("/register.dat"));
    	
    	waitWsEvent();
    	Assert.assertNotNull("No response received", _websocket.getMessage());
    	
    	
    	assertSimilar(getRawMessage("/registerResponse.dat"), _websocket.getRawResponse());
    }
    
    
    @Test
	/**
	 * <pre>
	 * Web socket
	 *  client                   Cipango UAS
	 *   | INVITE                     |
	 *   |--------------------------->|
	 *   |                        200 |
	 *   |<---------------------------|
	 *   | ACK                        |
	 *   |--------------------------->|
	 *   |                        BYE |
	 *   |<---------------------------|
	 *   |                        200 |
	 *   |--------------------------->|
	 * </pre>
	 * 
	 */
    public void testUasCall() throws Exception
    {
    	_websocket.getConnection().sendMessage(getRawMessage("/inviteUas.dat"));
    	
    	SipResponse response = (SipResponse) _websocket.waitMessage();
    	Assert.assertNotNull("No response received", response);
    	Assert.assertEquals(200, response.getStatus());
    	SipURI uri = (SipURI) response.getAddressHeader(SipHeaders.CONTACT).getURI();
    	Assert.assertEquals("ws", uri.getTransportParam());
    	
    	_websocket.getConnection().sendMessage(createAck(response).toString());
    	
    	Assert.assertTrue("No BYE received", (_websocket.waitMessage() instanceof SipRequest));
    	SipRequest request = (SipRequest) _websocket.getMessage();
    	Assert.assertEquals("BYE", request.getMethod());
    	_websocket.getConnection().sendMessage(new SipResponse(request, 200, "OK").toString());
    	Thread.sleep(50);
    }
    
    @Test
    /**
     * <pre>
	 * Web socket    proxy      SIP UA
	 *   client
	 *     |  INVITE    |          |
	 *     |----------->|          |
	 *     |            |INVITE    |
	 *     |            |--------->|
	 *     |            |      200 |
	 *     |            |<---------|
	 *     |       200  |          |
	 *     |<-----------|          |
	 *     | ACK        |          |
	 *     |----------->|          |
	 *     |            |ACK       |
	 *     |            |--------->|
	 *     |            |      BYE |
	 *     |            |<---------|
	 *     |        BYE |          |
	 *     |<-----------|          |
	 *     |       200  |          |
	 *     |----------->|          |
	 *     |            |      200 |
	 *     |            |--------->|
	 * </pre>
     */
    public void testProxyCall() throws Exception
    {
    	SipClient sipClient = new SipClient("localhost", 15070);
    	sipClient.start();
    	TestUserAgent userAgent = new TestUserAgent(new SipProfile("proxy", "cipango.org"));
    	sipClient.addAgent(userAgent);
    	
    	_websocket.getConnection().sendMessage(getRawMessage("/inviteProxy.dat"));
    	
    	SipServletRequest request = userAgent.waitInitialRequest();
    	Assert.assertNotNull(request);
    	System.out.println(request);
    	
    	// Ensure that there is a double route
    	Iterator<Address> it = request.getAddressHeaders(SipHeaders.RECORD_ROUTE);
    	Assert.assertTrue(it.hasNext());
    	Address address = it.next();
    	Assert.assertNull(((SipURI) address.getURI()).getTransportParam());
    	Assert.assertTrue(it.hasNext());
    	address = it.next();
    	Assert.assertEquals("ws", ((SipURI) address.getURI()).getTransportParam());
    	Assert.assertFalse(it.hasNext());
    	
    	request.createResponse(200).send();
    	
    	// Ensure that the proxy in invoked only once
    	SipResponse response = (SipResponse) _websocket.waitMessage();
    	Assert.assertNotNull("No response received", response);
    	Assert.assertEquals(200, response.getStatus());
    	Iterator<String> it2 = response.getHeaders("mode");
    	Assert.assertTrue(it2.hasNext());
    	it2.next();
    	Assert.assertFalse(it2.hasNext());
    	
    	_websocket.getConnection().sendMessage(createAck(response).toString());
    	
    	request = userAgent.waitSubsequentRequest();
    	Assert.assertNotNull(request);
    	Assert.assertEquals("ACK", request.getMethod());
    	
    	request.getSession().createRequest("BYE").send();
    	
    	Assert.assertTrue("No BYE received", (_websocket.waitMessage() instanceof SipRequest));
    	request = (SipRequest) _websocket.getMessage();
    	Assert.assertEquals("BYE", request.getMethod());
    	_websocket.getConnection().sendMessage(new SipResponse((SipRequest) request, 200, "OK").toString());
    	
    	SipServletResponse response2 = userAgent.waitResponse();
    	Assert.assertNotNull("No response received", response2);
    	Assert.assertEquals(200, response2.getStatus());
    }
    
    
    private SipRequest createAck(SipResponse response) throws ServletParseException
    {
    	SipRequest request = new SipRequest();
    	request.setMethod(SipMethods.ACK);
    	SipFields fields = request.getFields();
    	request.setRequestURI(response.getAddressHeader(SipHeaders.CONTACT).getURI());
    	fields.setAddress(SipHeaders.FROM, response.getFrom());
    	fields.setAddress(SipHeaders.TO, response.getTo());
    	fields.setString(SipHeaders.CALL_ID, response.getCallId());
    	fields.setString(SipHeaders.CSEQ_BUFFER, "1 ACK");
    	Via via = new Via("SIP/2.0/WS 127.0.0.1:20565;branch=z9hG4bK56sdasks");
    	via.setBranch(ID.newBranch());
    	fields.addVia(via, true);
    	ListIterator<String> it = response.getHeaders(SipHeaders.RECORD_ROUTE);
    	while (it.hasNext())
    		it.next();
    	while (it.hasPrevious())
			fields.addString(SipHeaders.ROUTE_BUFFER, it.previous());
    	
    	System.out.println(request.toString());
    	return request;
    }
 
    
    public SipMessage getSipMessage(String msg) throws Exception
    {
    	EventHandler handler = new EventHandler();
		SipParser parser = new SipParser(new ByteArrayBuffer(msg.getBytes()), handler);
		parser.parse();
		return handler.getMessage();
    }
    
    /**
     * Compare ignoring tag values.
     * @param expected
     * @param actual
     */
    private void assertSimilar(String expected, String actual)
    {
    	int i = 0;
    	while (true)
		{
    		int j = expected.indexOf('\n', i);
    		if (j == -1)
    			break;
			String lineExpected = expected.substring(i, j);
			String lineActual = actual.substring(i, j);
			int indexTag = lineExpected.indexOf("tag=");
			if (indexTag == -1)
				indexTag = lineExpected.indexOf("app-session-id=");
			if (indexTag != -1)
			{
				lineExpected = lineExpected.substring(0, indexTag);
				lineActual = lineActual.substring(0, indexTag);
			}
			Assert.assertEquals(lineExpected, lineActual);
			i = j + 1;
		}
    	
    }
    
    private String getRawMessage(String name) throws IOException
    {
		InputStream is = getClass().getResourceAsStream(name);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int read;
		while ((read = is.read(buffer)) != -1)
		{
			os.write(buffer, 0, read);
		}
		String message = new String(os.toByteArray());
		message = message.replaceAll("\\$\\{callId\\}", ID.newCallId());
		message = message.replaceAll("\\$\\{host\\}", "127.0.0.1");
		message = message.replaceAll("\\$\\{port\\}", String.valueOf(__port));
		
		return message;

    }
    
    class TestWebSocket implements WebSocket.OnTextMessage
    {
    	private String _message;
    	private Connection _connection;
    	
    	 public void onOpen(Connection connection)
         {
        	 _connection = connection;
        	synchronized (this)
  			{
                   notify();
  			}
         }

         public void onMessage(String data)
         {
        	 if (!data.startsWith("SIP/2.0 100 Trying"))
        	 {
	        	 _message = data;
	             System.out.println("data = " + data);
	             synchronized (this)
				{
	                 notify();
				}
        	 }
         }

         public void onClose(int closeCode, String message)
         {
        	 _connection = null;
         }

		public SipMessage getMessage() throws Exception
		{
			if (_message == null)
				return null;
			return getSipMessage(_message);
		}
		
		public SipMessage waitMessage() throws Exception
		{
			synchronized (this)
			{
				wait(2000);
			}
			return getMessage();
		}
		
		public String getRawResponse() throws Exception
		{
			return _message;
		}

		public boolean isOpen()
		{
			return _connection != null;
		}

		public Connection getConnection()
		{
			return _connection;
		}
    }

    static class TestUserAgent extends UserAgent implements MessageHandler
    {
    	private SipServletRequest _initialRequest;
    	private SipServletRequest _subsequentRequest;
    	private SipServletResponse _response;
    	
		public TestUserAgent(SipProfile profile)
		{
			super(profile);
		}

		@Override
		public void handleInitialRequest(SipServletRequest request)
		{
			_initialRequest = request;
			_initialRequest.getSession().setAttribute(MessageHandler.class.getName(), this);
			synchronized (this)
			{
				notify();
			}
		}

		public SipServletRequest getInitialRequest()
		{
			return _initialRequest;
		}
		
		public SipServletRequest waitInitialRequest() throws InterruptedException
		{
			synchronized (this)
			{
				wait(2000);
			}
			return _initialRequest;
		}

		public void handleRequest(SipServletRequest request) throws IOException, ServletException
		{
			_subsequentRequest = request;
			synchronized (this)
			{
				notify();
			}
		}

		public void handleResponse(SipServletResponse response) throws IOException, ServletException
		{
			_response = response;
			synchronized (this)
			{
				notify();
			}
		}

		public SipServletRequest waitSubsequentRequest() throws InterruptedException
		{
			synchronized (this)
			{
				wait(2000);
			}
			return _subsequentRequest;
		}

		public SipServletResponse waitResponse() throws InterruptedException
		{
			synchronized (this)
			{
				wait(2000);
			}
			return _response;
		}
		
    	
    }
}
