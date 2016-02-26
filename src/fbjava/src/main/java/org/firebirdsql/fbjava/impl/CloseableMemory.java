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

import com.sun.jna.Memory;


/***
 * JNA Memory with AutoCloseable.
 *
 * @author asfernandes
 */
class CloseableMemory extends Memory implements AutoCloseable
{
	public CloseableMemory(long size)
	{
		super(size);
	}

	@Override
	public void close()
	{
		dispose();
	}
}
