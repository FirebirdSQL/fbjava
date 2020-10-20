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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.firebirdsql.fbjava.CallableRoutineContext;
import org.firebirdsql.fbjava.ExternalResultSet;
import org.firebirdsql.fbjava.ProcedureContext;
import org.firebirdsql.fbjava.Values;
import org.firebirdsql.fbjava.ValuesMetadata;


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
		};
	}

	public static void p8(int i1, int i2, String[] o1, int[] o2, int[] o3) throws SQLException
	{
		CallableRoutineContext context = CallableRoutineContext.get();
		o1[0] = context.getInputMetadata().getParameterCount() + ", " + context.getOutputMetadata().getParameterCount();
	}

	public static void p9(int i1, Integer i2, String[] o1) throws SQLException
	{
		CallableRoutineContext context = CallableRoutineContext.get();
		ValuesMetadata input = context.getInputMetadata();
		ValuesMetadata output = context.getOutputMetadata();

		o1[0] = Functions.getValuesInfo(input, 1) + ", " + Functions.getValuesInfo(input, 2) + ", " +
			Functions.getValuesInfo(output, 1);
	}

	public static void p10(Object i1, Object i2, String[] o1) throws SQLException
	{
		CallableRoutineContext context = CallableRoutineContext.get();
		ValuesMetadata input = context.getInputMetadata();
		ValuesMetadata output = context.getOutputMetadata();

		o1[0] = Functions.getValuesInfo(input, 1) + ", " + Functions.getValuesInfo(input, 2) + ", " +
			Functions.getValuesInfo(output, 1);
	}

	public static void p11(Object i1, Object i2, String[] o1) throws SQLException
	{
		ProcedureContext context = ProcedureContext.get();
		ValuesMetadata inputMetadata = context.getInputMetadata();
		ValuesMetadata outputMetadata = context.getOutputMetadata();
		Values input = context.getInputValues();
		Values output = context.getOutputValues();

		if (o1[0] != output.getObject(1))
			throw new RuntimeException("Error");

		o1[0] = "a";

		if (o1[0] != output.getObject(1))
			throw new RuntimeException("Error");

		output.setObject(1, "b");

		if (o1[0] != output.getObject(1))
			throw new RuntimeException("Error");

		output.setObject(1,
			Functions.getValues(inputMetadata, input) + " / " + Functions.getValues(outputMetadata, output));
	}

	public static ExternalResultSet p12()
	{
		ProcedureContext context = ProcedureContext.get();
		Values outValues = context.getOutputValues();

		BigDecimal i = (BigDecimal) context.getInputValues().getObject(1);
		outValues.setObject(1, i);

		return new ExternalResultSet() {
			@Override
			public boolean fetch() throws Exception
			{
				BigDecimal o = ((BigDecimal) outValues.getObject(1)).add(BigDecimal.ONE);
				outValues.setObject(1, o);
				return o.subtract(i).intValue() <= 5;
			}
		};
	}

	public static ResultSet testResultSet(long[] o1, String[] o2) throws SQLException {
		Connection con = DriverManager.getConnection("jdbc:new:connection:");
		PreparedStatement pstmt = con.prepareStatement(
			"select mon$attachment_id, mon$user from mon$attachments where mon$attachment_id = current_connection");
		return pstmt.executeQuery();
	}
}
