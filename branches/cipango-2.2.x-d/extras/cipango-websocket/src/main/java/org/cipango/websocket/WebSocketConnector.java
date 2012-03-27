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
package org.cipango.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.SipURI;

import org.cipango.server.Server;
import org.cipango.server.SipConnection;
import org.cipango.server.SipConnector;
import org.cipango.server.SipConnectors;
import org.cipango.server.SipHandler;
import org.cipango.server.SipMessage;
import org.cipango.server.bio.UdpConnector.EventHandler;
import org.cipango.sip.SipParser;
import org.cipango.sip.SipURIImpl;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.WebSocket;

public class WebSocketConnector extends AbstractLifeCycle implements SipConnector
{
	private static final Logger LOG = Log.getLogger(WebSocketConnection.class);
	
	public static final int WS_DEFAULT_PORT = 80;
	public static final int WSS_DEFAULT_PORT = 443;
	
	private Connector _httpConnector;
	private InetAddress _localAddr;
	private Map<String, WebSocketConnection> _connections;
	private SipURI _sipUri;
    private SipHandler _handler;
    private Server _server;
    private ThreadPool _threadPool;
	
	public WebSocketConnector(Connector connector)
	{
		_httpConnector = connector;
		try
		{
			_localAddr =  InetAddress.getByName(_httpConnector.getHost());
		}
		catch (Exception e) 
		{
			LOG.warn(e);
		}
		_connections = new HashMap<String, WebSocketConnection>();
	}
	
	@Override
    protected void doStart() throws Exception 
    {    	
    	_sipUri = new SipURIImpl(null, getHost(), getPort());
    	_sipUri.setTransportParam(getTransport().toLowerCase());
    	
    	if (_threadPool == null && _server != null)
        	_threadPool = _server.getSipThreadPool();
    	
        open();        
        LOG.info("Started {}", this);
    }
	
	public void open() throws IOException
	{
	}

	public void close() throws IOException
	{
		// FIXME close connections ???
	}
	
	public String getHost()
	{
		return _httpConnector.getHost();
	}

	public int getPort()
	{
		return _httpConnector.getPort();
	}

	public String getExternalHost()
	{
		return null;
	}

	public String getTransport()
	{
		if (isSecure())
			return SipConnectors.WSS;
		return SipConnectors.WS;
	}

	public SipURI getSipUri()
	{
		return _sipUri;
	}

	public void setServer(Server server)
	{
		_server = server;
	}

	public void setHandler(SipHandler handler)
	{
		_handler = handler;
	}

	public long getNbParseError()
	{
		return 0;
	}

	public void setStatsOn(boolean on)
	{
	}

	public void statsReset()
	{
	}

	public InetAddress getAddr()
	{
		return _localAddr;
	}

	public int getLocalPort()
	{
		return _httpConnector.getLocalPort();
	}

	public Object getConnection()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public int getTransportOrdinal()
	{
		if (isSecure())
			return SipConnectors.WSS_ORDINAL;
		return SipConnectors.WS_ORDINAL;
	}

	public int getDefaultPort()
	{
		if (isSecure())
			return WSS_DEFAULT_PORT;
		return WS_DEFAULT_PORT;
	}
	
    public ThreadPool getThreadPool()
    {
        return _threadPool;
    }
    
    public void setThreadPool(ThreadPool threadPool)
    {
    	_threadPool = threadPool;
    }

    public void process(SipMessage message)
    {
    	if (!isRunning())
    		return;
    	
    	if (!getThreadPool().dispatch(new MessageTask(message)))
		{
    		LOG.warn("No threads to dispatch message from {}:{}",
					message.getRemoteAddr(), message.getRemotePort());
		}
    }
	
	public boolean isReliable()
	{
		return true;
	}

	public boolean isSecure()
	{
		return false; // FIXME todo
	}

	public SipConnection getConnection(InetAddress addr, int port) throws IOException
	{
		synchronized (_connections)
		{
			return _connections.get(key(addr, port));
		}
	}
	
	public WebSocketConnection addConnection(HttpServletRequest request)
	{
		WebSocketConnection connection = new WebSocketConnection(request);
		synchronized (_connections)
		{
			_connections.put(key(connection), connection);
		}
		return connection;
	}
	
	public void removeConnection(WebSocketConnection connection)
	{
		synchronized (_connections)
		{
			_connections.put(key(connection), connection);
		}
	}
	
	private String key(WebSocketConnection connection) 
	{
		return key(connection.getRemoteAddress(), connection.getRemotePort());
	}
	
	private String key(InetAddress addr, int port) 
	{
		return addr.getHostAddress() + ":" + port;
	}

	class WebSocketConnection implements SipConnection, WebSocket.OnTextMessage
	{		
		private InetAddress _localAddr;
		private int _localPort;
		private InetAddress _remoteAddr;
		private int _remotePort;
		private Connection _connection;
		
		public WebSocketConnection(HttpServletRequest request)
		{
			try
			{
				_localAddr = InetAddress.getByName(request.getLocalAddr());
				_localPort = request.getLocalPort();
				_remoteAddr = InetAddress.getByName(request.getRemoteAddr());
				_remotePort = request.getRemotePort();
			}
			catch (Exception e) 
			{
				LOG.warn(e);
			}

		}
		
		
		public SipConnector getConnector()
		{
			return WebSocketConnector.this;
		}

		public InetAddress getLocalAddress()
		{
			return _localAddr;
		}

		public int getLocalPort()
		{
			return _localPort;
		}

		public InetAddress getRemoteAddress()
		{
			return _remoteAddr;
		}

		public int getRemotePort()
		{
			return _remotePort;
		}

		public void write(Buffer buffer) throws IOException
		{
			_connection.sendMessage(buffer.toString());
		}

		public boolean isOpen()
		{
			return _connection != null;
		}


		public void onOpen(Connection connection)
		{
			_connection = connection;
		}


		public void onClose(int closeCode, String message)
		{
			_connection = null;
			removeConnection(this);
		}


		public void onMessage(String data)
		{
			Buffer buffer = new ByteArrayBuffer(data.getBytes());
			
			EventHandler handler = new EventHandler();
			SipParser parser = new SipParser(buffer, handler);
			
			try
			{
				parser.parse();
				
				SipMessage message = handler.getMessage();
				message.setConnection(this);			
				process(message);
			}
			catch (Throwable t) 
			{
				LOG.warn(t);
				//if (handler.hasException())
					//Log.warn(handler.getException());
	        
				if (LOG.isDebugEnabled())
					LOG.debug("Buffer content: \r\n" + data);

			}
		}
		
	}
	
	class MessageTask implements Runnable
    {
    	private SipMessage _message;
    	
    	public MessageTask(SipMessage message)
    	{
    		_message = message;
    	}
    	
    	public void run()
    	{
    		try 
    		{
    			_handler.handle(_message);
    		}
    		catch (Exception e)
    		{
    			LOG.warn(e);
    		}
    	}
    }
}
