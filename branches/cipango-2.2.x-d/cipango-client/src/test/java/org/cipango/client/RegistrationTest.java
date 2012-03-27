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

package org.cipango.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipURI;

import org.cipango.sip.SipURIImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegistrationTest 
{
	TestServer _server;
	SipClient _client;
	RegistrationListener _listener;
	
	@Before
	public void start() throws Exception 
	{
		_server = new TestServer("127.0.0.1", 5060);
		_server.start();
		
		_client = new SipClient("127.0.0.1", 5070);
		_client.start();
		
		_listener = new RegistrationListener();
	}
	
	@After
	public void stop() throws Exception
	{
		_server.stop();
		_client.stop();
	}
	
	protected void register(SipURI uri, int expires)
	{
		register(uri, null, expires);
	}
	
	protected void register(SipURI uri, Credentials credentials, int expires)
	{
		Registration registration = new Registration(uri);
		registration.setFactory(_client.getFactory());
		registration.setListener(_listener);
		
		registration.setCredentials(credentials);
		
		registration.register(_client.getContact(), expires);
	}
	
	@Test
	public void forbidden() throws Exception 
	{
		register(new SipURIImpl("sip:unknown@127.0.0.1"), 60);

		assertTrue(_listener.gotResponse());
		assertEquals(403, _listener.getStatus());
		assertNull(_listener.getRegisteredContact());
	}
	
	@Test
	public void success() throws Exception
	{
		register(new SipURIImpl("sip:alice@127.0.0.1"), new Credentials("alice", "alice"), 3600);
		
		assertTrue(_listener.gotResponse());
		assertNotNull(_listener.getRegisteredContact());
	}
	
	@Test
	public void invalidCredentials() throws Exception
	{
		register(new SipURIImpl("sip:alice@127.0.0.1"), new Credentials("alice", "invalid"), 3600);
		assertTrue(_listener.gotResponse());
		assertEquals(401, _listener.getStatus());
	}
	
	class RegistrationListener implements Registration.Listener
	{
		private CountDownLatch _latch = new CountDownLatch(1);
		private volatile Address _contact;
		private volatile int _expires;
		private int _status;
		
		public boolean gotResponse() throws InterruptedException
		{
			return _latch.await(5, TimeUnit.SECONDS);
		}
		
		public Address getRegisteredContact() 
		{
			return _contact;
		}
		
		public int getExpires()
		{
			return _expires;
		}
		
		public int getStatus()
		{
			return _status;
		}
		
		public void registrationFailed(int status) 
		{
			_status = status;
			_latch.countDown();
		}

		public void registrationDone(Address contact, int expires, List<Address> contacts) 
		{
			_contact = contact;
			_expires = expires;
			_latch.countDown();
		}
	}
}
