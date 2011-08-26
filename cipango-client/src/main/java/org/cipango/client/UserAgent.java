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

import javax.servlet.sip.Address;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;

public class UserAgent 
{
	private SipProfile _profile;
	private Address _contact;
	private SipFactory _factory;
	
	public UserAgent(SipProfile profile)
	{
		_profile = profile;
	}
	
	public SipProfile getProfile()
	{
		return _profile;
	}
	
	public void setContact(Address contact)
	{
		_contact = contact;
	}
	
	public Address getContact()
	{
		return _contact;
	}
	
	public void setFactory(SipFactory factory)
	{
		_factory = factory;
	}
	
	public SipFactory getFactory()
	{
		return _factory;
	}
	
	public void handleInitialRequest(SipServletRequest request)
	{
		
	}
}
