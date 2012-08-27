// ========================================================================
// Copyright 2008-2012 NEXCOM Systems
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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;

import org.cipango.server.log.AccessLog;
import org.cipango.sip.NameAddr;
import org.cipango.sip.SipGenerator;
import org.cipango.sip.SipHeaders;
import org.cipango.sip.Via;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.Buffers;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ConnectorManager extends AbstractLifeCycle implements Buffers, SipHandler
{
	private static final Logger LOG = Log.getLogger(ConnectorManager.class);
	
    private static final int DEFAULT_MESSAGE_SIZE = 16*1024; // FIXME
    private static final int MAX_MESSAGE_SIZE = 64*1024;
    // By default set MTU to max message size instead of 1500.
    private static final int DEFAULT_MTU = MAX_MESSAGE_SIZE;
    
    private Server _server;
   
    private SipConnector[] _connectors;
    private int _mtu = DEFAULT_MTU;
    
    private SipGenerator _sipGenerator;
    
    private AccessLog _accessLog;
    
    private final AtomicLong _receivedStats = new AtomicLong();
    private final AtomicLong _sentStats = new AtomicLong();
    
    private transient long _nbParseErrors;
    
    private ArrayList<Buffer> _buffers;
    private int _messageSize = 10000;
    
    private int _largeMessageSize = MAX_MESSAGE_SIZE;
    
    private boolean _forceClientRport;
        
    public void addConnector(SipConnector connector) 
    {
        setConnectors((SipConnector[]) LazyList.addToArray(getConnectors(), connector, SipConnector.class));
    }
    
    public SipConnector[] getConnectors()
    {
        return _connectors;
    }
    
    public SipConnector getDefaultConnector()
    {
    	if (_connectors == null || _connectors.length == 0)
    		return null;
    	return _connectors[0];
    }
    
    public void setConnectors(SipConnector[] connectors)
    {
        if (connectors != null) 
        {
            for (int i = 0; i < connectors.length; i++)
            {
                SipConnector connector = connectors[i];
                connector.setServer(_server);
                connector.setHandler(this);
            }
        }
        if (_server != null)
        	_server.getContainer().update(this, _connectors, connectors, "connectors");
        _connectors = connectors;
    }
    
    public void setServer(org.eclipse.jetty.server.Server server)
    {
    	_server = (Server) server;
    }
    
    public Server getServer()
    {
    	return _server;
    }
    
    public Address getContact(int type)
    {
        SipConnector sc = findConnector(type, null);
        return new NameAddr((URI) sc.getSipUri().clone());
        //return (Address) findTransport(type, null).getContact().clone();
    }
    
    
    protected void doStart() throws Exception
    {
    	super.doStart();

        if (_buffers != null)
    		_buffers.clear();
    	else
    		_buffers = new ArrayList<Buffer>();
        
        _sipGenerator = new SipGenerator();
        
        if (_accessLog instanceof LifeCycle)
        {
        	try
        	{
        		((LifeCycle) _accessLog).start();
        	}
        	catch (Exception e)
        	{
        		LOG.warn("failed to start access log", e);
        	}
        }
        
        if (_connectors != null)
        {
	        for (int i = 0; i < _connectors.length; i++)
	        {
	            SipConnector connector = _connectors[i];
	            connector.start();
	        }
        }
    }

    protected void doStop() throws Exception
    {
        MultiException mex = new MultiException();
        
        if (_connectors != null)
        {
            for (int i = _connectors.length; i--> 0;)
            {
                try
                {
                    _connectors[i].stop();
                } 
                catch(Throwable e)
                {
                    mex.add(e);
                }
            }
        }
        

    	if (_accessLog instanceof LifeCycle)
    		try { ((LifeCycle) _accessLog).stop(); } catch (Throwable t) { LOG.warn(t); }
        
        super.doStop();
        
        mex.ifExceptionThrow();
    }
    
    public SipConnector findConnector(int type, InetAddress addr)
    {
        for (int i = 0; i < _connectors.length; i++) 
        {
            SipConnector t = _connectors[i];
            if (t.getTransportOrdinal() == type) 
                return t;
        }
        return _connectors[0];
    }
    
    public void messageReceived()
    {
    	_receivedStats.incrementAndGet();
    }
    
    public void messageSent()
    {
        _sentStats.incrementAndGet();
    }
        
    public void handle(SipServletMessage message) throws IOException, ServletException
    {   
    	SipMessage msg = (SipMessage) message;
    	
    	messageReceived();
    	
    	if (_accessLog != null)
    		_accessLog.messageReceived(msg, msg.getConnection());
        
        if (preValidateMessage((SipMessage) message))
		{
        	if (msg.isRequest())
            {
                Via via = msg.getTopVia();
                String remoteAddr = msg.getRemoteAddr();
                
                String host = via.getHost();
                if (host.indexOf('[') != -1)
                {
                	// As there is multiple presentation of an IPv6 address, normalize it.
                	host = InetAddress.getByName(host).getHostAddress();
                }
                
                if (!host.equals(remoteAddr))
                    via.setReceived(remoteAddr);

                if (via.getRport() != null || isForceClientRport())
                    via.setRport(Integer.toString(message.getRemotePort()));
            }

            getServer().handle(msg);
		}
		else
		{
			_nbParseErrors++;
		}  
    }
    
    public boolean isLocalUri(URI uri)
    {
        if (!uri.isSipURI())
            return false;
        
        SipURI sipUri = (SipURI) uri;

        if (!sipUri.getLrParam())
            return false;

        String host = sipUri.getHost();
        
        // Normalize IPv6 address
		if (host.indexOf("[") != -1) 
		{
			try
			{
				host = InetAddress.getByName(host).getHostAddress();
			}
			catch (UnknownHostException e)
			{
				LOG.ignore(e);
			}
		}
		
        for (int i = 0; i < _connectors.length; i++)
        {
            SipConnector connector = _connectors[i];
            
            String connectorHost = connector.getSipUri().getHost();
            
            boolean samePort = connector.getPort() == sipUri.getPort() || sipUri.getPort() == -1;
            if (samePort)
            {
	            if ((connectorHost.equals(host) || connector.getAddr().getHostAddress().equals(host))) 
	            {
	            	if (sipUri.getPort() != -1)
	            		return true;
	            	
	            	// match on host address and port is not set ==> NAPTR case
	            	if (connector.getAddr().getHostAddress().equals(host)
	            			&& connector.getPort() != connector.getDefaultPort())
	            	{
	            		return false;
	            	}
	            	return true;
	            }
            }
        }
        return false;
    }
    
    /**
     * Sends the message and returns the connection used to sent the message.
     * The returned connection can be different if initial connection is not reliable and
     * message is bigger than MTU.
     */
    public SipConnection send(SipMessage message, SipConnection connection) throws IOException
    {
    	Buffer buffer = getBuffer(_messageSize); 
    	_sipGenerator.generate(buffer, message);
    	    	
    	try
    	{
        	if (!connection.getConnector().isReliable() 
        			&& (buffer.putIndex() + 200 > _mtu)
        			&& message.isRequest()) {
    			LOG.debug("Message is too large. Switching to TCP");
    			try
    			{
    				SipConnection newConnection = getConnection((SipRequest) message, 
	    					SipConnectors.TCP_ORDINAL, 
	    					connection.getRemoteAddress(), 
	    					connection.getRemotePort());
	    			if (newConnection.getConnector().isReliable())
	    			{
	    				return send(message, newConnection);
	    			}
    			}
    			catch (IOException e) 
    			{
    				Via via = message.getTopVia();
    				// Update via to ensure that right value is used in logs
    		        SipConnector connector = connection.getConnector();
    		        via.setTransport(connector.getTransport());
    		        via.setHost(connector.getSipUri().getHost());
    		        via.setPort(connector.getSipUri().getPort());
    				LOG.debug("Failed to switch to TCP, return to original connection");
				}
    		}
    		
    		connection.write(buffer);
    		
    		if (_accessLog != null)
    			_accessLog.messageSent(message, connection);
            messageSent();
            return connection;
    	}
    	finally
    	{
    		returnBuffer(buffer);
    	}
    }
    
    public SipConnection getConnection(SipRequest request, int transport, InetAddress address, int port) throws IOException
    {   
    	SipConnector connector = findConnector(transport, address);
    	
        Via via = request.getTopVia();
        
        via.setTransport(connector.getTransport());
        via.setHost(connector.getSipUri().getHost());
        via.setPort(connector.getSipUri().getPort());
                
        SipConnection connection = connector.getConnection(address, port);
        if (connection == null)
        	throw new IOException("Could not find connection to " + address + ":" + port + "/" + connector.getTransport());
        
        return connection;
    }
    
    public void sendResponse(SipResponse response) throws IOException
    {
    	SipRequest request = (SipRequest) response.getRequest();
    	SipConnection connection = null;
    	
    	if (request != null)
    		connection = request.getConnection();
    	
    	sendResponse(response, connection);
    }
    
    public void sendResponse(SipResponse response, SipConnection connection) throws IOException
    {
    	if (connection == null || !connection.getConnector().isReliable() || !connection.isOpen())
    	{
    		Via via = response.getTopVia();
    		
    		SipConnector connector = null;
    		InetAddress address = null;
    		
    		if (connection != null)
    		{
    			connector = connection.getConnector();
    			address = connection.getRemoteAddress();
    		}
    		else
    		{
    			int transport = SipConnectors.getOrdinal(via.getTransport());
    			
    			if (via.getMAddr() != null)
    				address = InetAddress.getByName(via.getMAddr());
    			else
    				address = InetAddress.getByName(via.getHost());
    			
    			connector = findConnector(transport, address);
    		}
    		
			int port = -1;
			
			String srport = via.getRport();
	        if (srport != null) 
	        {
	            port = Integer.parseInt(srport);
	        } 
	        else 
	        {
	            port = via.getPort();
	            if (port == -1) 
	                port = connection.getConnector().getDefaultPort();
	        }
	        connection = connector.getConnection(address, port);
	        
	        if (connection == null)
	        	throw new IOException("Could not found any SIP connection to " 
	        			+ address + ":" + port + "/" + connector.getTransport());
    	}
    	send(response, connection);
    }
    
    /*
    public void send(SipResponse response, SipRequest request) throws IOException 
    {
    	SipConnector connector = null;
    	
    	if (request != null && request.getEndpoint() != null)
    	{
			SipEndpoint endpoint = request.getEndpoint();
    		connector = endpoint.getConnector();
    		
    		if (connector.isReliable() && endpoint.isOpen())
    		{
	        	Buffer buffer = getBuffer(_messageSize);
	        	_sipGenerator.generate(buffer, response);
				try
				{
					endpoint.getConnector().doSend(buffer, endpoint);
					
					for (int i = 0; _loggers != null && i < _loggers.length; i++)
					{
						EndPoint ep = (EndPoint) endpoint;
			        	_loggers[i].messageSent(
			        			response, 
			        			connector.getTransportOrdinal(), 
			        			ep.getLocalAddr(),
			        			ep.getLocalPort(), 
			        			ep.getRemoteAddr(), 
			        			ep.getRemotePort());    				        
					}
					messageSent();
					return;
				}
				finally
				{
					returnBuffer(buffer);
				}
    		}
    	}

    	int transport = -1; 
    	InetAddress address = null;
    	int port = -1;
    	
    	if (request != null)
    		transport = request.transport();
    	else
    		transport = SipConnectors.getOrdinal(response.getTopVia().getTransport());
    	

        Via via = response.getTopVia();
        
		if (request != null)
			address = request.remoteAddress();
		else
			address = InetAddress.getByName(via.getHost());
    	
    	if (connector == null)
    		connector = findConnector(transport, address);
        
        String srport = via.getRport();
        if (srport != null) 
        {
            port = Integer.parseInt(srport);
        } 
        else 
        {
            port = via.getPort();
            if (port == -1) 
                port = SipConnectors.getDefaultPort(transport);
        }
        
        Buffer buffer = getBuffer(_messageSize); 
    	_sipGenerator.generate(buffer, response);
    	try
    	{
    		connector.send(buffer, address, port);
    	}
    	finally 
    	{
    		returnBuffer(buffer);
    	}
    	
    	
    	for (int i = 0; _loggers != null && i < _loggers.length; i++)
        	_loggers[i].messageSent(
        			response, 
        			connector.getTransportOrdinal(), 
        			connector.getAddr().getHostAddress(), 
        			connector.getPort(), 
        			address.getHostAddress(), 
        			port);  
        
        messageSent();
    }
    */
    
    public Buffer getBuffer(int size) 
    {
		if (size == _messageSize)
		{
			synchronized (_buffers)
			{
				if (_buffers.size() == 0)
					return newBuffer(size);
	            return (Buffer) _buffers.remove(_buffers.size() - 1);
            }
        }
		else 
			return newBuffer(size);
    }
    
    public void returnBuffer(Buffer buffer)
    {
        buffer.clear();
        int c = buffer.capacity();
        if (c == _messageSize)
        {
	        synchronized (_buffers)
	        {
	            _buffers.add(buffer);
	        }
        }
    }
    
    public Buffer newBuffer(int size)
    {
    	return new ByteArrayBuffer(size);
    }
    
    
    public static void putStringUTF8(Buffer buffer, String s) 
    {
        byte[] bytes = null;
        try 
        {
            bytes = s.getBytes("UTF-8");
        } 
        catch (UnsupportedEncodingException e) 
        {
            throw new RuntimeException();
        }
        buffer.put(bytes);
    }
   
    public void setAccessLog(AccessLog accessLog)
    {

        if (getServer() != null)
            getServer().getContainer().update(this, _accessLog, accessLog, "accessLog", false);
        
        _accessLog = accessLog;
        
        try
        {
        	if (isRunning() && _accessLog instanceof LifeCycle)
        		((LifeCycle) accessLog).start();
        }
        catch (Exception e)
        {
            LOG.warn(e);
        }
    }
    
    public long getMessagesReceived() 
    {
        return _receivedStats.get();
    }
    
    public long getMessagesSent() 
    {
        return _sentStats.get();
    }
    
	public long getNbParseError()
	{
		long val = _nbParseErrors;
		for (int i = 0; _connectors != null && i <_connectors.length; i++)
		{
			val += _connectors[i].getNbParseError();
		}
		return val;
	}
    
    public void statsReset() 
    {
    	_receivedStats.set(0);
    	_sentStats.set(0);
    	
        _nbParseErrors = 0;
        for (int i = 0; _connectors != null && i <_connectors.length; i++)
        {
			 _connectors[i].statsReset();
		}
    }
       
	public boolean preValidateMessage(SipMessage message)
	{
		boolean valid = true;
		try 
		{
			if (!isUnique(SipHeaders.FROM_BUFFER, message)
					|| !isUnique(SipHeaders.TO_BUFFER, message)
					|| !isUnique(SipHeaders.CALL_ID_BUFFER, message)
					|| !isUnique(SipHeaders.CSEQ_BUFFER, message))
			{
				valid = false;
			}
			else if (message.getTopVia() == null
					|| message.getFrom() == null
					|| message.getTo() == null
					|| message.getCSeq() == null)
			{
				LOG.info("Received bad message: unparsable required headers");
				valid = false;
			}
			message.getAddressHeader("contact");
				
			if (message instanceof SipRequest)
			{
				SipRequest request = (SipRequest) message;
				if (request.getRequestURI() == null)
					valid = false;
				request.getTopRoute();
				if (!request.getCSeq().getMethod().equals(request.getMethod()))
				{
					LOG.info("Received bad request: CSeq method does not match");
					valid = false;
				}
			}
			else
			{
				int status = ((SipResponse) message).getStatus();
				if (status < 100 || status > 699)
				{
					LOG.info("Received bad response: Invalid status code: " + status);
					valid = false;
				}
			}
		}
		catch (Exception e) 
		{
			LOG.info("Received bad message: Some headers are not parsable: {}", e);
			LOG.debug("Received bad message: Some headers are not parsable", e);
			valid = false;
		}
				
		try 
		{
			if (!valid 
					&& message instanceof SipRequest 
					&& !message.isAck()
					&& message.getTopVia() != null)
			{
				// TODO send response stateless
				SipResponse response = 
					(SipResponse) ((SipRequest) message).createResponse(SipServletResponse.SC_BAD_REQUEST);
				sendResponse(response);
			}
		}
		catch (Exception e) 
		{
			LOG.ignore(e);
		}
		
		return valid;
	}
	
	private boolean isUnique(Buffer headerName, SipMessage message)
	{
		Iterator<String> it = message.getFields().getValues(headerName);
		if (!it.hasNext())
		{
			LOG.info("Received bad message: Missing required header: " + headerName);
			return false;
		}
		it.next();
		if (it.hasNext())
			LOG.info("Received bad message: Duplicate header: " + headerName);
		return !it.hasNext();
	}

	public AccessLog getAccessLog()
	{
		return _accessLog;
	}
		 
	public Buffer getBuffer() {
		// TODO Auto-generated method stub
		return null;
	}

	public Buffer getHeader() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getMtu()
	{
		return _mtu;
	}

	public void setMtu(int mtu)
	{
		_mtu = mtu;
	}
	
	public boolean isForceClientRport()
	{
		return _forceClientRport;
	}
	
	public void setForceClientRport(boolean forceClientRport)
	{
		_forceClientRport = forceClientRport;
	}


}
