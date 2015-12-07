package example;

import java.math.BigDecimal;


public class Functions
{
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
}
