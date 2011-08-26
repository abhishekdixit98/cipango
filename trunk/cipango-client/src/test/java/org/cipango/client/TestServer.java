package org.cipango.client;

import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;
import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.SipApplicationRouterInfo;
import javax.servlet.sip.ar.SipApplicationRoutingDirective;
import javax.servlet.sip.ar.SipApplicationRoutingRegion;
import javax.servlet.sip.ar.SipRouteModifier;
import javax.servlet.sip.ar.SipTargetedRequestInfo;

import org.cipango.server.Server;
import org.cipango.server.bio.UdpConnector;
import org.cipango.server.handler.SipContextHandlerCollection;
import org.cipango.servlet.SipServletHolder;
import org.cipango.sip.SipHeaders;
import org.cipango.sipapp.SipAppContext;

import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.QuotedStringTokenizer;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;

public class TestServer extends AbstractLifeCycle
{
	private Server _server;
	private SipAppContext _context;
	
	public TestServer(String host, int port)
	{
		_server = new Server();
		
		UdpConnector connector = new UdpConnector();
		connector.setHost(host);
		connector.setPort(port);
		
		_server.getConnectorManager().addConnector(connector);
		_server.setApplicationRouter(new ApplicationRouter());
		
		SipContextHandlerCollection handler = new SipContextHandlerCollection();
		_server.setHandler(handler);
		
		_context = new SipAppContext();
		_context.setConfigurationClasses(new String[0]);
		_context.setContextPath("/");
		_context.setName(TestServer.class.getName());
		
		SipServletHolder holder = new SipServletHolder();
		holder.setServlet(new TestServerServlet());
		holder.setName(TestServerServlet.class.getName());
		
		_context.getSipServletHandler().addSipServlet(holder);
		_context.getSipServletHandler().setMainServletName(TestServerServlet.class.getName());
		
		handler.addHandler(_context);
	}
	
	public TestServer(int port)
	{
		this (null, port);
	}
	
	@Override
	protected void doStart() throws Exception
	{
		_server.start();
	}
	
	@Override
	protected void doStop() throws Exception
	{
		_server.stop();
	}
	
	@SuppressWarnings("serial")
	class TestServerServlet extends SipServlet
	{
		private int _defaultExpires = 3600;
		private int _minExpires = 3600;
		private int _maxExpires = 3600;
		
		private SipFactory _sipFactory;
		private DateFormat _dateFormat;
		
		private Random _random = new Random();
		private Map<String, Set<Binding>> _bindings;
		private Map<String, String> _users;
		
		@Override
		public void init()
		{
			_sipFactory = (SipFactory) getServletContext().getAttribute(SipServlet.SIP_FACTORY);

			_dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
			_dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			
			_users = new HashMap<String, String>();
			_users.put("alice@127.0.0.1", "alice");
			
			_bindings = new HashMap<String, Set<Binding>>();
		}
		
		@Override
		protected void doRequest(SipServletRequest request) throws ServletException, IOException 
		{
			super.doRequest(request);
		}
		
		public Set<Binding> getBindings(String aor, boolean create)
		{
			Set<Binding> bindings;
			synchronized(_bindings)
			{
				bindings = _bindings.get(aor);
				
				if (bindings == null && create)
				{
					bindings = new CopyOnWriteArraySet<TestServer.Binding>();
					_bindings.put(aor, bindings);
				}
				return bindings;
			}
		}
		
		public String getAor(Address address)
		{
			SipURI uri = (SipURI) address.getURI();
			return uri.getUser() + "@" + uri.getHost().toLowerCase(); 
		}
		
		public boolean checkAuthorization(String aor, String method, String authorization)
		{
			if (authorization == null)
				return false;
			
			QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(authorization, "=, ", true, false);
            Digest digest = new Digest(method);
            
            String last = null;
            String name = null;

            while (tokenizer.hasMoreTokens())
            {
                String tok = tokenizer.nextToken();
                char c = (tok.length() == 1) ? tok.charAt(0) : '\0';

                switch (c)
                {
                    case '=':
                        name = last;
                        last = tok;
                        break;
                    case ',':
                        name = null;
                    case ' ':
                        break;

                    default:
                        last = tok;
                        if (name != null)
                        {
                            if ("username".equalsIgnoreCase(name))
                                digest.username = tok;
                            else if ("realm".equalsIgnoreCase(name))
                                digest.realm = tok;
                            else if ("nonce".equalsIgnoreCase(name))
                                digest.nonce = tok;
                            else if ("nc".equalsIgnoreCase(name))
                                digest.nc = tok;
                            else if ("cnonce".equalsIgnoreCase(name))
                                digest.cnonce = tok;
                            else if ("qop".equalsIgnoreCase(name))
                                digest.qop = tok;
                            else if ("uri".equalsIgnoreCase(name))
                                digest.uri = tok;
                            else if ("response".equalsIgnoreCase(name)) 
                                digest.response = tok;
                            name=null;
                        }
                }
            }

            String password = _users.get(aor);
            
            return digest.check(password);
		}
		
		protected void doRegister(SipServletRequest register) throws ServletException, IOException
		{
			System.out.println("got register: " + register);
			
			try
			{
				String aor = getAor(register.getTo());
				
				if (!_users.containsKey(aor))
				{
					register.createResponse(SipServletResponse.SC_FORBIDDEN).send();
					return;
				}
				
				SipURI uri = (SipURI) register.getTo().getURI();
				
				if (!checkAuthorization(aor, register.getMethod(), register.getHeader(SipHeaders.AUTHORIZATION)))
				{
					SipServletResponse response = register.createResponse(SipServletResponse.SC_UNAUTHORIZED);
					byte[] nonce = new byte[16];
					_random.nextBytes(nonce);
					
					response.setHeader(SipHeaders.WWW_AUTHENTICATE, "Digest realm=\"cipango.org\"" 
							+ ", qop=\"auth\", nonce=\"" 
							+ new String(TypeUtil.toString(nonce, 16))
							+ "\", opaque=\"\", stale=FALSE, algorithm=MD5");
					response.send();
					return;
				}
				
				Set<Binding> bindings = getBindings(aor, true);
				
				Iterator<Address> it = register.getAddressHeaders("Contact");
				
				if (it.hasNext())
				{
					List<Address> contacts = new ArrayList<Address>();
					boolean wildcard = false;
					
					while (it.hasNext())
					{
						Address contact = it.next();
						if (contact.isWildcard())
						{
							wildcard = true;
							if (it.hasNext() || contacts.size() > 0 || register.getExpires() > 0)
							{
								register.createResponse(SipServletResponse.SC_BAD_REQUEST, "Invalid wildcard").send();	
								return;
							}
						}
						contacts.add(contact);
					}
					String callId = register.getCallId();
					
					int cseq;
					
					try
					{
						String s = register.getHeader("CSeq");
						cseq = Integer.parseInt(s.substring(0, s.indexOf(' ')));
					}
					catch (Exception e)
					{
						register.createResponse(SipServletResponse.SC_BAD_REQUEST, e.getMessage()).send();
						return;
					}
					
					if (wildcard)
					{
						// TODO
					}
					else
					{
						for (Address contact : contacts)
						{
							int expires = -1;
							expires = contact.getExpires();
							
							if (expires < 0)
								expires = register.getExpires();
							
							if (expires != 0)
							{
								if (expires < 0)
									expires = _defaultExpires;
								
								if (expires > _maxExpires)
								{
									expires = _maxExpires;
								}
								else if (expires < _minExpires)
								{
									SipServletResponse response = register.createResponse(423);
									response.addHeader("Min-Expires", Integer.toString(_minExpires));
									response.send();
									return;
								}
							}
							boolean exist = false;
							
							Iterator<Binding> it2 = bindings.iterator();
							while (it2.hasNext())
							{
								Binding binding = it2.next();
								
								if (contact.getURI().equals(binding.getContact()))
								{
									exist = true;
									if (callId.equals(binding.getCallId()) && cseq < binding.getCSeq())
									{
										register.createResponse(SipServletResponse.SC_SERVER_INTERNAL_ERROR,
												"cseq too low").send();
										return;
									}
									if (expires == 0)
									{
										it2.remove();
									}
									else
									{
										binding.setCallId(callId);
										binding.setCSeq(cseq);
										binding.setExpiryTime(System.currentTimeMillis() + expires * 1000l);
									}
								}
							}
							if (!exist && expires != 0)
							{
								Binding binding = new Binding(aor, contact.getURI());
								binding.setCallId(callId);
								binding.setCSeq(cseq);
								binding.setExpiryTime(System.currentTimeMillis() + expires * 1000l);
								bindings.add(binding);
							}
						}
					}
				}
				
				SipServletResponse ok = register.createResponse(SipServletResponse.SC_OK);
				if (bindings.size() > 0)
				{
					long now = System.currentTimeMillis();
					
					for (Binding binding : bindings)
					{
						Address address = _sipFactory.createAddress(binding.getContact());
						address.setExpires((int)((binding.getExpiryTime() - now) / 1000l));
						ok.addAddressHeader("Contact", address, false);
					}
				}
				ok.addHeader("Date", _dateFormat.format(new Date()));
				ok.send();
				
			}
			catch (Throwable t)
			{
				t.printStackTrace();
				SipServletResponse response = register.createResponse(SipServletResponse.SC_SERVER_INTERNAL_ERROR);
				response.send();				
			}
		}
		
		@Override
		protected void doResponse(SipServletResponse response) throws ServletException, IOException
		{
		}
	}
	
	class Binding
	{
		private String _aor;
		private URI _contact;
		private String _callId;
		private int _cseq;
		private long _expiryTime;
		
		public Binding(String aor, URI contact)
		{
			_aor = aor;
			_contact = contact;
		}
		
		public String getAor()
		{
			return _aor;
		}
		
		public String getCallId()
		{
			return _callId;
		}
		
		public int getCSeq()
		{
			return _cseq;
		}
		
		public URI getContact()
		{
			return _contact;
		}
		
		public void setCallId(String callId)
		{
			_callId = callId;
		}
		
		public void setCSeq(int cseq)
		{
			_cseq = cseq;
		}
		
		public void setExpiryTime(long time)
		{
			_expiryTime = time;
		}
		
		public long getExpiryTime()
		{
			return _expiryTime;
		}
	}
	
	private static class Digest 
    {
        String method = null;
        String username = null;
        String realm = null;
        String nonce = null;
        String nc = null;
        String cnonce = null;
        String qop = null;
        String uri = null;
        String response = null;

        Digest(String m)
        {
            method = m;
        }

        public boolean check(String password)
        {
            try
            {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] ha1;
                    // calc A1 digest
                md.update(username.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(realm.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(password.getBytes(StringUtil.__ISO_8859_1));
                ha1 = md.digest();
                // calc A2 digest
                md.reset();
                md.update(method.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(uri.getBytes(StringUtil.__ISO_8859_1));
                byte[] ha2 = md.digest();

                // calc digest
                // request-digest = <"> < KD ( H(A1), unq(nonce-value) ":"
                // nc-value ":" unq(cnonce-value) ":" unq(qop-value) ":" H(A2) )
                // <">
                // request-digest = <"> < KD ( H(A1), unq(nonce-value) ":" H(A2)
                // ) > <">

                md.update(TypeUtil.toString(ha1, 16).getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(nonce.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(nc.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(cnonce.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(qop.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(TypeUtil.toString(ha2, 16).getBytes(StringUtil.__ISO_8859_1));
                byte[] digest = md.digest();

                // check digest
                return (TypeUtil.toString(digest, 16).equalsIgnoreCase(response));
            }
            catch (Exception e)
            {
                Log.warn(e);
            }

            return false;
        }
    }
	
	class ApplicationRouter implements SipApplicationRouter
	{
		public void init() { }
		
		public void init(Properties properties) { }
		
		public void destroy() { }
		
		public void applicationDeployed(List<String> deployedApplications) { }
		
		public void applicationUndeployed(List<String> undeployedApplications) { }

		public SipApplicationRouterInfo getNextApplication(SipServletRequest request,
				SipApplicationRoutingRegion region, SipApplicationRoutingDirective directive,
				SipTargetedRequestInfo requestedInfo, Serializable info)
		{
			if (request.getRemoteAddr() == null)
				return null;
			return new SipApplicationRouterInfo(TestServer.class.getName(),
					SipApplicationRoutingRegion.NEUTRAL_REGION, 
					request.getFrom().getURI().toString(), 
					null,
					SipRouteModifier.NO_ROUTE, 
					1);
		}
	}

}
