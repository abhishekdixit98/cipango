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
package org.cipango.console;

import java.io.IOException;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.cipango.callflow.CallflowNotificationFilter;
import org.cipango.console.printer.logs.SipLogPrinter;
import org.cipango.console.printer.logs.SipLogPrinter.MessageLog;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

public class SipLogServlet extends WebSocketServlet
{
	private ConsoleFilter _consoleFilter;
	private Logger _logger = Log.getLogger("console");

	@Override
	public void init() throws ServletException
	{
		super.init();
		_consoleFilter = (ConsoleFilter) getServletContext().getAttribute(ConsoleFilter.class.getName());
	}

	public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol)
	{
		return new SipLogWebSocket(request.getSession());
	}

	public class SipLogWebSocket implements WebSocket, NotificationListener
	{

		private Connection _connection;
		private HttpSession _session;
		
		public SipLogWebSocket(HttpSession session)
		{
			_session = session;
		}

		public void onOpen(Connection connection)
		{
			_connection = connection;
			try
			{
				CallflowNotificationFilter filter =  (CallflowNotificationFilter) _session.getAttribute(Parameters.SIP_MESSAGE_FILTER);
				if (filter == null)
				{
					filter = new CallflowNotificationFilter(null);
					_session.setAttribute(Parameters.SIP_MESSAGE_FILTER, filter);
				}
				
				_consoleFilter.getMbsc().addNotificationListener(ConsoleFilter.SIP_CONSOLE_MSG_LOG, this,
						filter, null);
			}
			catch (Exception e)
			{
				_logger.warn(e);
			}
		}

		public void onClose(int closeCode, String message)
		{
			_connection = null;
			try
			{
				_consoleFilter.getMbsc().removeNotificationListener(ConsoleFilter.SIP_CONSOLE_MSG_LOG, this);
			}
			catch (Exception e)
			{
				_logger.warn(e);
			}
		}

		public void onMessage(byte frame, String data)
		{
			try
			{
				_connection.sendMessage(data);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		public void onMessage(byte frame, byte[] data, int offset, int length)
		{
		}

		public void handleNotification(Notification notification, Object handback)
		{
			try
			{
				StringBuilder sb = new StringBuilder();
				Object[] data = (Object[]) notification.getUserData();
				MessageLog log = new MessageLog(data);
				String info = log.getInfoLine().replaceFirst(log.getRemote(),
						SipLogPrinter.getFilterLink(SipLogPrinter.REMOTE_FILTER, log.getRemote()));
				sb.append("<div class=\"msg-info\">" + info + "</div>");
				sb.append("<pre class=\"message\">");
				sb.append(SipLogPrinter.sipToHtml(log.getMessage()));
				sb.append("</pre>");
				sb.append("</div>");
				
				_connection.sendMessage(sb.toString());
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

}
