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
 * This interface represents a Firebird Values (set of input/output parameters or trigger's old/new fields) metadata.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public interface ValuesMetadata
{
	/**
	 * Gets the number of values.
	 */
	public int getCount();

	/**
	 * Gets the name for a given value index.
	 * Returns null in function output metadata.
	 */
	public String getName(int index);

	/**
	 * Gets the Java Class for a given value index.
	 */
	public Class<?> getJavaClass(int index);

	/**
	 * Gets the Java SQL type (java.sql.Types.*) for a given value index.
	 * It returns the raw type (i.e. not NUMERIC or DECIMAL, but INTERGER, SMALLINT, etc).
	 */
	public int getSqlType(int index);

	//// TODO: more methods
}
