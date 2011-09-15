package org.cipango.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import junit.framework.Assert;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @version $Revision$ $Date$
 */
public class WebSocketTest
{
    private static WebSocketClientFactory _factory = new WebSocketClientFactory();
    private static final String BASE_URL = "ws://127.0.0.1:8078/test-cipango-websocket/";
    
    @BeforeClass
    public static void startServer() throws Exception
    {
        _factory.start();
    }

    @AfterClass
    public static void stopServer() throws Exception
    {
        _factory.stop();
    }

    @Test
    public void testSimpleMessage() throws Exception
    {
    	WebSocketClient client = new WebSocketClient(_factory);
    	TestWebSocket websocket = new TestWebSocket();
    	
    	client.open(new URI(BASE_URL), websocket);
    	
    	synchronized (websocket)
		{
			websocket.wait(2000);
		}
    	Assert.assertTrue("Websocket is not open", websocket.isOpen());
    	Assert.assertNotNull("No response received", websocket.getResponse());
    	assertSimilar(getMessage("/registerResponse.dat"), websocket.getResponse());
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
			String lineActual = expected.substring(i, j);
			int indexTag = lineExpected.indexOf("tag=");
			if (indexTag != -1)
			{
				lineExpected = lineExpected.substring(0, indexTag);
				lineActual = lineActual.substring(0, indexTag);
			}
			Assert.assertEquals(lineExpected, lineActual);
			i = j + 1;
		}
    	
    }
    
    private String getMessage(String name) throws IOException
    {
		InputStream is = getClass().getResourceAsStream(name);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int read;
		while ((read = is.read(buffer)) != -1)
		{
			os.write(buffer, 0, read);
		}
		return new String(os.toByteArray());

    }
    
    class TestWebSocket implements WebSocket.OnTextMessage
    {
    	private String _response;
    	private boolean _open = false;
    	private Throwable _throwable;
    	
    	 public void onOpen(Connection connection)
         {
        	 try
        	 {
        		 connection.sendMessage(getMessage("/register.dat"));
        	 }
        	 catch (IOException e)
     		{
        		 e.printStackTrace();
     			throw new RuntimeException(e);
     		}
        	 _open = true;
         }

         public void onMessage(String data)
         {
        	 _response = data;
             System.out.println("data = " + data);
             synchronized (this)
			{
                 notify();
			}
         }

         public void onClose(int closeCode, String message)
         {
        	 _open = false;
         }

		public String getResponse()
		{
			return _response;
		}

		public boolean isOpen()
		{
			return _open;
		}

		public Throwable getThrowable()
		{
			return _throwable;
		}
    }

}
