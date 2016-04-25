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
package example;

import org.firebirdsql.fbjava.ExternalResultSet;


public class Procedures
{
	public static void p1()
	{
	}

	public static void p2(int i)
	{
	}

	public static void p3(int i, int[] o)
	{
		o[0] = i;
	}

	public static ExternalResultSet p4(int i, int[] o)
	{
		o[0] = i;

		return new ExternalResultSet() {
			@Override
			public boolean fetch() throws Exception
			{
				return ++o[0] <= i + 5;
			}

			@Override
			public void close()
			{
			}
		};
	}

	public static ExternalResultSet p5(int start, int end, int[] o)
	{
		o[0] = start - 1;

		return new ExternalResultSet() {
			@Override
			public boolean fetch() throws Exception
			{
				return ++o[0] <= end;
			}

			@Override
			public void close()
			{
			}
		};
	}

	public static ExternalResultSet p6(int start, int end, int[] o)
	{
		o[0] = start - 1;

		return new ExternalResultSet() {
			@Override
			public boolean fetch() throws Exception
			{
				if (o[0] == 10)
					throw new Exception("Can't go beyond 10.");

				return ++o[0] <= end;
			}

			@Override
			public void close()
			{
			}
		};
	}

	public static ExternalResultSet p7(String property, String[] result)
	{
		return new ExternalResultSet() {
			boolean first = true;

			@Override
			public boolean fetch() throws Exception
			{
				if (first)
				{
					result[0] = System.getProperty(property);
					first = false;
					return true;
				}
				else
					return false;
			}

			@Override
			public void close()
			{
			}
		};
	}
}
