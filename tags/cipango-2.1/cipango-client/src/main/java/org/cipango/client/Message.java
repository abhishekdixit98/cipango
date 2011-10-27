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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import org.cipango.sip.SipMethods;

public class Message 
{
	private SipFactory _factory;
	
	private SipURI _from;
	private SipURI _to;
	
	private Listener _listener;
	private MessageHandler _handler = new Handler();
	
	public interface Listener
	{
		void messageSucceded();
		void messageFailed();
	}
	
	public Message(SipURI from, SipURI to)
	{
		_from = from;
		_to = to;
	}
	
	public void setListener(Listener listener)
	{
		_listener = listener;
	}
	
	public void setFactory(SipFactory factory)
	{
		_factory = factory;
	}
	
	protected SipServletRequest createMessage(SipURI from, SipURI to)
	{
		SipApplicationSession appSession = _factory.createApplicationSession();
		SipServletRequest message = _factory.createRequest(appSession, SipMethods.MESSAGE, from, to);
		
		message.getSession().setAttribute(MessageHandler.class.getName(), _handler);		
		
		return message;
	}
	
	public void send()
	{
		try 
		{
			createMessage(_from, _to).send();
		} 
		catch (Exception e)
		{
			// TODO
		}
	}
	
	class Handler implements MessageHandler
	{
		public void handleRequest(SipServletRequest request) throws IOException, ServletException 
		{
			request.createResponse(SipServletResponse.SC_NOT_ACCEPTABLE_HERE).send();
		}
	
		public void handleResponse(SipServletResponse response) throws IOException, ServletException 
		{
			int status = response.getStatus();
			
			if (_listener != null)
			{
				if (status >= 200 && status < 300)
					_listener.messageSucceded();
				else if (status >= 300)
					_listener.messageFailed();
			}
		}
	}
}
