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
import java.net.InetAddress;

import javax.servlet.sip.SipURI;

import org.eclipse.jetty.util.component.LifeCycle;

/**
 * Connector for the SIP protocol.
 * A connector receives and sends SIP messages. Connectors are managed by the {@link ConnectorManager}
 */
public interface SipConnector extends LifeCycle
{    
	/**
	 * Open the connector
	 * @throws IOException
	 */
	void open() throws IOException;
	
	/**
	 * Close the connector
	 * @throws IOException
	 */
	void close() throws IOException;

	/**
	 * @return the hostname (host or IP address) of the interface on which the connector will bind
	 */
    String getHost();

	/**
	 * @return the port on which the the connector will bind
	 */
	int getPort();

    /**
     * @return the actual address on which the connector is bound
     */
	InetAddress getAddr();
	
	/**
	 * @return the port on which the the connector will bind
	 */
	int getLocalPort();
	
	/**
	 * @return the underlying socket for this connector
	 */
	Object getConnection();

	String getExternalHost();

    String getTransport(); 
    int getTransportOrdinal();
	int getDefaultPort();
	boolean isReliable();
	boolean isSecure();
	
    SipURI getSipUri();
    //Via getVia(); // TODO buffer
    //void setTransportParam(boolean b);
    
    SipConnection getConnection(InetAddress addr, int port) throws IOException;
     
    void setServer(Server server);
    void setHandler(SipHandler handler);
    
    //SipEndpoint send(Buffer buffer, InetAddress address, int port) throws IOException;
    //void send(Buffer buffer, SipEndpoint endpoint) throws IOException;
    
    long getNbParseError();
    
    
    void setStatsOn(boolean on);
    //boolean getStatsOn();
    void statsReset();
}
