package org.cipango.diameter.node;

public interface TimeoutHandler
{
	public void fireNoAnswerReceived(DiameterRequest request, long timeout);
}
