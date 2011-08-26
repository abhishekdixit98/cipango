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

import javax.servlet.sip.SipURI;

import org.cipango.sip.SipURIImpl;

public class SipProfile 
{
	private SipURI _uri;
	private Credentials _credentials;
	
	public SipProfile(String username, String domain)
	{
		String host = domain;
		int port = -1;
		
		int i = domain.indexOf(":");
		if (i != -1)
		{
			host = domain.substring(0, i);
			port = Integer.parseInt(domain.substring(i+1));
		}
		_uri = new SipURIImpl(username, host, port);
	}
	
	public void setCredentials(Credentials credentials)
	{
		_credentials = credentials;
	}
	
	public Credentials getCredentials()
	{
		return _credentials;
	}
	
	public String getUsername()
	{
		return _uri.getUser();
	}
	
	public String getDomain()
	{
		return _uri.getPort() == -1 ? _uri.getHost() : _uri.getHost() + ":" + _uri.getPort();
	}
	
	public SipURI getURI()
	{
		return _uri;
	}
}
