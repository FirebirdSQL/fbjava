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
package org.firebirdsql.fbjava;


/**
 * This interface represents a Firebird External ResultSet for a selectable stored procedure.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public interface ExternalResultSet
{
	/**
	 * Called by Firebird to get records from the ExternalResultSet.
	 * @return	false to stop and true to continue.
	 * @throws Exception
	 */
	public boolean fetch() throws Exception;

	/**
	 * Called by Firebird after fetched all rows from the ExternalResultSet.
	 */
	public default void close()
	{
	}
}
