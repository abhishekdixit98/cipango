package org.cipango.osgi.api;

import java.util.Dictionary;
import java.util.EventListener;

import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSessionAttributeListener;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipSessionAttributeListener;

/**
 * The Sip Service allows other bundles in the OSGi environment to dynamically
 * register SIP servlets and listeners. A bundle may later unregister its servlets.
 * 
 */
public interface SipService {
	
	/**
	 * This OSGi environment property specifies the port used for servlets accessible via SIP.
	 * The default value for this property is 5060.
	 */
	public static final String SIP_PORT = "sip.port";
	
	/**
	 * This OSGi environment property specifies the host used for servlets accessible via SIP.
	 * If not defined, the first public will be used.
	 */
	public static final String SIP_HOST = "sip.host";
		
	/**
	 * Register servlet. 
	 * The first servlet is used as main servlet. The servlet name is read from Sip Servlet annotations.
	 * @param servlet the servlet object to register
	 * @param initparams initialization arguments for the servlet or
	 *        <code>null</code> if there are none. This argument is used by the
	 *        servlet's <code>ServletConfig</code> object.
	 * @throws javax.servlet.ServletException if the servlet's <code>init</code>
	 *            method throws an exception, or the given servlet object has
	 *            already been registered with a different name.
	 */
	public void registerServlet(SipServlet servlet, Dictionary<String, String> initparams)
			throws ServletException;
	
	/**
	 * Register servlet. 
	 * The first servlet is used as main servlet. The servlet name is read from Sip Servlet annotations.
	 * @param servlet the servlet object to register
	 * @throws javax.servlet.ServletException if the servlet's <code>init</code>
	 *            method throws an exception, or the given servlet object has
	 *            already been registered with a different name.
	 */
	public void registerServlet(SipServlet servlet) throws ServletException;
	
	/**
	 * Register the listener <code>l</code> to the <code>context</code>.
	 * <p>
	 * If the listener implements multiple EventListener interface defined in
	 * Servlet or Sip servlet API, all interfaces of them will be registered.
	 * 
	 * @param listener The listener to register.
	 * @throws ServletException if the listener cannot be registered.
	 * 
	 * @see SipApplicationSessionListener
	 * @see SipApplicationSessionAttributeListener
	 * @see SipErrorListener
	 * @see SipServletListener
	 * @see SipSessionListener
	 * @see SipSessionActivationListener
	 * @see SipSessionBindingListener
	 * @see SipSessionAttributeListener
	 * @see TimerListener
	 * @see ServletContextListener
	 * @see ServletContextAttributeListener
	 */
	public void registerListener(EventListener l) throws ServletException;

	/**
	 * Unregisters a previous registration done by <code>registerServlet</code> methods.
	 * <p>
	 * After this call, the registered servlet will no longer be available. The
	 * Sip Service must call the <code>destroy</code> method of the servlet before
	 * returning.
	 * <p>
	 * If the bundle which performed the registration is stopped or otherwise
	 * "unget"s the Sip Service without calling {@link #unregister}then Sip
	 * Service must automatically unregister the registration. However, if the
	 * registration was for a servlet, the <code>destroy</code> method of the
	 * servlet will not be called in this case since the bundle may be stopped.
	 * {@link #unregister}must be explicitly called to cause the
	 * <code>destroy</code> method of the servlet to be called. This can be done
	 * in the <code>BundleActivator.stop</code> method of the
	 * bundle registering the servlet.
	 * <p>
	 * Note: If the servlet has been also registered as a listener, the listener will
	 * remains active after degistration.
	 * 
	 * @param servlet the servlet to unregister
	 * @throws java.lang.IllegalArgumentException if there is no registration
	 *            for the name or the calling bundle was not the bundle which
	 *            registered the name.
	 */
	public void unregister(String servletName);
	
	public void unregister(SipServlet servlet);

}
