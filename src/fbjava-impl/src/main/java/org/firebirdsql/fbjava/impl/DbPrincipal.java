/*
 * FB/Java plugin
 *
 * Distributable under LGPL license.
 * You may obtain a copy of the License at http://www.gnu.org/copyleft/lgpl.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * LGPL License for more details.
 *
 * This file was created by members of the Firebird development team.
 * All individual contributions remain the Copyright (C) of those
 * individuals.  Contributors to this file are either listed here or
 * can be obtained from a git log command.
 *
 * All rights reserved.
 */
package org.firebirdsql.fbjava.impl;

import java.security.Principal;


/**
 * A database user for security handling purposes.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
final class DbPrincipal implements Principal
{
	private final String databaseName;
	private final String roleName;
	private final String userName;

	public DbPrincipal(final String databaseName, final String roleName, final String userName)
	{
		this.databaseName = databaseName;
		this.roleName = roleName;
		this.userName = userName;
	}

	@Override
	public String getName()
	{
		return userName;
	}

	@Override
	public boolean equals(final Object another)
	{
		return (another instanceof DbPrincipal) &&
			databaseName.equals(((DbPrincipal) another).databaseName) &&
			roleName.equals(((DbPrincipal) another).roleName) &&
			userName.equals(((DbPrincipal) another).userName);
	}

	@Override
	public int hashCode()
	{
		return userName.hashCode();
	}

	@Override
	public String toString()
	{
		return databaseName + "::" + userName;
	}

	String getDatabaseName()
	{
		return databaseName;
	}

	public String getRoleName()
	{
		return roleName;
	}
}
