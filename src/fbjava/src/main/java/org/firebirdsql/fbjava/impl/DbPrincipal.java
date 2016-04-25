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
	private String databaseName;
	private String name;

	public DbPrincipal(String databaseName, String name)
	{
		this.databaseName = databaseName;
		this.name = name;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public boolean equals(Object another)
	{
		return (another instanceof DbPrincipal) &&
			databaseName.equals(((DbPrincipal) another).databaseName) &&
			name.equals(((DbPrincipal) another).name);
	}

	@Override
	public int hashCode()
	{
		return name.hashCode();
	}

	@Override
	public String toString()
	{
		return databaseName + "::" + name;
	}

	String getDatabaseName()
	{
		return databaseName;
	}
}
