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
package org.cipango.dns.bio;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import org.cipango.dns.AbstractConnector;
import org.cipango.dns.DnsConnection;
import org.cipango.dns.DnsMessage;
import org.eclipse.jetty.io.ByteArrayBuffer;

public class UdpConnector extends AbstractConnector
{
	public static final int MAX_PACKET_SIZE = 512;
	
	
	public DnsConnection newConnection(InetAddress host, int port)
	{
		return new Connection(host, port);
	}
	
	public DatagramSocket newDatagramSocket() throws SocketException
	{
		return new DatagramSocket(getPort(), getHostAddr());
	}

	
	public class Connection implements DnsConnection
	{
		private InetAddress _remoteAddr;
		private int _remotePort;
		private DatagramSocket _socket;
		
		public Connection(InetAddress remoteAddr, int remotePort)
		{
			_remoteAddr = remoteAddr;
			_remotePort = remotePort;
		}
		
		public void send(DnsMessage message) throws IOException
		{
			ByteArrayBuffer buffer = new ByteArrayBuffer(MAX_PACKET_SIZE);
			message.encode(buffer);
			DatagramPacket packet = new DatagramPacket(buffer.asArray(), buffer.length(), _remoteAddr, _remotePort);
			
			_socket = newDatagramSocket();
			_socket.send(packet);
		}
		
		public DnsMessage waitAnswer(int timeout) throws IOException
		{
			DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
			_socket.setSoTimeout(timeout);
			_socket.receive(packet);
			DnsMessage message = new DnsMessage();
			message.decode(new ByteArrayBuffer(packet.getData()));
			_socket.close();
			return message;
		}
		
		public DatagramSocket getSocket()
		{
			return _socket;
		}
		
	}
}
