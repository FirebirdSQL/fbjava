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


final class Parameter
{
	Parameter(final DataType dataType, final Class<?> javaClass)
	{
		this.dataType = dataType;
		this.javaClass = javaClass;
	}

	final DataType dataType;
	final Class<?> javaClass;
	DataType.Conversion conversion;
	String name;
	Pair<String, Integer> type;	// Pair of String, java.sql.Types.*
	int nullOffset;
	int offset;
	int length;
	int scale;
	boolean isNullable;
}
