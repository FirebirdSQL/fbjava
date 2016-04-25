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

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class Functions
{
	private static int n = 0;

	public static int f1()
	{
		return -1234567890;
	}

	public static int f2()
	{
		return 2;
	}

	public static int f3()
	{
		throw new RuntimeException("f3");
	}

	public static Integer f4()
	{
		return null;
	}

	public static Integer f5(Integer p)
	{
		return p;
	}

	public static int f6(int p)
	{
		return p;
	}

	public static BigDecimal f7(BigDecimal p)
	{
		return p;
	}

	public static Object f8(Object p)
	{
		return p;
	}

	public static Double f9(Double p)
	{
		return p;
	}

	public static long f10a(long p)
	{
		return p;
	}

	public static Long f10b(Long p)
	{
		return p;
	}

	public static boolean f11a(boolean p)
	{
		return p;
	}

	public static Boolean f11b(Boolean p)
	{
		return p;
	}

	public static java.util.Date f12a(java.util.Date p)
	{
		return p;
	}

	public static java.sql.Date f12b(java.sql.Date p)
	{
		return p;
	}

	/*** FIXME:
	public static java.util.Date f13a(java.util.Date p)
	{
		return p;
	}
	***/

	public static java.sql.Timestamp f13b(java.sql.Timestamp p)
	{
		return p;
	}

	public static java.sql.Time f14b(java.sql.Time p)
	{
		return p;
	}

	public static Integer f15a(byte[] p)
	{
		return p != null ? p.length : null;
	}

	public static byte[] f16a(byte[] p)
	{
		return p;
	}

	public static Blob f17a(Blob p)
	{
		return p;
	}

	public static String f18a(String p)
	{
		return p;
	}

	public static int f19() throws SQLException
	{
		int n = 0;

		for (int i = 0; i < 2; ++i)
		{
			try (Connection connection = DriverManager.getConnection("jdbc:default:connection"))
			{
				try (Statement statement = connection.createStatement())
				{
					try (ResultSet rs = statement.executeQuery("select 11 from rdb$database"))
					{
						rs.next();
						n += rs.getInt(1);
					}
				}
			}
		}

		return n;
	}

	public static int f20()
	{
		return ++n;
	}

	public static String f21(String property)
	{
		return System.getProperty(property);
	}
}
