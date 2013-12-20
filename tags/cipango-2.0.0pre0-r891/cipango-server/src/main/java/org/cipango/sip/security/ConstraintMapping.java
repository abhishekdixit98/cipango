// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
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

import java.util.List;

public class ConstraintMapping
{
    private List<String> _methods;

    private List<String> _servletNames;

    private Constraint _constraint;

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the constraint.
     */
    public Constraint getConstraint()
    {
        return _constraint;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param constraint The constraint to set.
     */
    public void setConstraint(Constraint constraint)
    {
        _constraint = constraint;
    }

	public List<String> getMethods()
	{
		return _methods;
	}

	public void setMethods(List<String> methods)
	{
		_methods = methods;
	}

	public List<String> getServletNames()
	{
		return _servletNames;
	}

	public void setServletNames(List<String> servletNames)
	{
		_servletNames = servletNames;
	}

}
