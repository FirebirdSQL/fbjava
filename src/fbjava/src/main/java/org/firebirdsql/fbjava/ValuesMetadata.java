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

import java.sql.ParameterMetaData;


/**
 * This interface represents a Firebird Values (set of input/output parameters or trigger's old/new fields) metadata.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public interface ValuesMetadata extends ParameterMetaData
{
	/**
	 * Gets the index for a given name.
	 * Returns -1 if the name is not found.
	 */
	public int getIndex(String name);

	/**
	 * Gets the name for a given value index.
	 * Returns null in function output metadata.
	 */
	public String getName(int index);

	/**
	 * Gets the Java Class for a given value index.
	 */
	public Class<?> getJavaClass(int index);
}
