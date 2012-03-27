// ========================================================================
// Copyright (c) 2008-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.cipango.sip.security;

import javax.servlet.ServletContext;

import org.cipango.sip.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.http.security.Constraint;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Server;

/* ------------------------------------------------------------ */

public class DefaultAuthenticatorFactory implements SipAuthenticator.Factory
{

	public SipAuthenticator getAuthenticator(Server server, ServletContext context,
			org.cipango.sip.security.SipAuthenticator.AuthConfiguration configuration,
			IdentityService identityService, LoginService loginService)
	{
		String auth=configuration.getAuthMethod();
		if (Constraint.__DIGEST_AUTH.equalsIgnoreCase(auth))
			return new DigestAuthenticator();
		return null;
	}
   


}
