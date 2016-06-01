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

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;

import javax.security.auth.Subject;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;
import org.firebirdsql.gds.ISCConstants;
import org.firebirdsql.gds.TransactionParameterBuffer;
import org.firebirdsql.jdbc.FBConnection;


/**
 * Security policy configured in a database.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
final class DbPolicy extends Policy
{
	private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
	private static int count;
	private static String securityDatabase;
	private static FBConnection securityDb;
	private static PreparedStatement stmt;
	private static HashMap<String, Subject> userSubjects = new HashMap<String, Subject>();

	public DbPolicy(String securityDatabase)
	{
		DbPolicy.securityDatabase = securityDatabase;
	}

	static void databaseOpened() throws SQLException
	{
		synchronized (securityDatabase)
		{
			if (count++ == 0)
			{
				assert securityDb == null;

				securityDb = (FBConnection) DriverManager.getConnection(
					"jdbc:firebirdsql:embedded:" + securityDatabase + "?charSet=UTF-8");
				securityDb.setAutoCommit(false);
				securityDb.setReadOnly(true);

				TransactionParameterBuffer tpb = securityDb.createTransactionParameterBuffer();
				tpb.addArgument(ISCConstants.isc_tpb_read_committed);
				tpb.addArgument(ISCConstants.isc_tpb_rec_version);
				tpb.addArgument(ISCConstants.isc_tpb_read);
				securityDb.setTransactionParameters(Connection.TRANSACTION_READ_COMMITTED, tpb);

				stmt = securityDb.prepareStatement(
					"select p.class_name, p.arg1, p.arg2\n" +
					"  from permission_group_grant pgg\n" +
					"  join permission_group pg\n" +
					"    on pg.id = pgg.permission_group\n" +
					"  join permission p\n" +
					"    on p.permission_group = pg.id\n" +
					"  where cast(? as varchar(1024)) similar to " + windowsUpper("pgg.database_pattern") +
					"          escape '&' and\n" +
					"        ((pgg.grantee_type = 'USER' and\n" +
					"          cast(? as varchar(512)) similar to pgg.grantee_pattern escape '&') or\n" +
					"         (pgg.grantee_type = 'ROLE' and\n" +
					"          cast(? as varchar(512)) similar to pgg.grantee_pattern escape '&'))");
			}
		}
	}

	static void databaseClosed() throws SQLException
	{
		synchronized (securityDatabase)
		{
			if (--count == 0)
			{
				stmt.close();
				securityDb.close();
				securityDb = null;
			}
		}
	}

	static synchronized Subject getUserSubject(IStatus status, IExternalContext context, DbClassLoader classLoader)
		throws Exception
	{
		String databaseName = classLoader.databaseName;
		String userName = context.getUserName();
		String key = databaseName + "\0" + userName;

		synchronized (userSubjects)
		{
			Subject subj = userSubjects.get(key);
			if (subj != null)
				return subj;

			String roleName;

			try (InternalContext internalContext = InternalContext.create(status, context, null, null, null))
			{
				try (Connection conn = internalContext.getConnection())
				{
					try (Statement stmt = conn.createStatement())
					{
						try (ResultSet rs = stmt.executeQuery("select current_role from rdb$database"))
						{
							rs.next();
							roleName = rs.getString(1);
						}
					}
				}
			}

			final HashSet<Principal> principals = new HashSet<Principal>();
			principals.add(new DbPrincipal(databaseName, roleName, userName));
			subj = new Subject(true, principals, new HashSet<Object>(), new HashSet<Object>());

			userSubjects.put(key, subj);

			return subj;
		}
	}

	@Override
	public PermissionCollection getPermissions(final ProtectionDomain domain)
	{
		final Permissions permissions = new Permissions();

		// Grant all permission to code stored at filesystem
		if ("file".equals(domain.getCodeSource().getLocation().getProtocol()))
		{
			permissions.add(new AllPermission());
			return permissions;
		}

		return AccessController.doPrivileged(new PrivilegedAction<PermissionCollection>() {
			@Override
			public PermissionCollection run()
			{
				for (Principal principal : domain.getPrincipals())
				{
					if (principal instanceof DbPrincipal)
					{
						DbPrincipal dbPrincipal = (DbPrincipal) principal;
						loadPermissions(dbPrincipal.getDatabaseName(), dbPrincipal.getRoleName(), dbPrincipal.getName(),
							permissions);
					}
				}

				return permissions;
			}
		});
	}

	@Override
	public boolean implies(final ProtectionDomain domain, final Permission permission)
	{
		// Grant all permission to code stored at filesystem
		if ("file".equals(domain.getCodeSource().getLocation().getProtocol()))
			return true;

		return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
			@Override
			public Boolean run()
			{
				PermissionCollection perms = getPermissions(domain);
				return perms.implies(permission);
			}
		});
	}

	private void loadPermissions(String databaseName, String roleName, String userName,
		PermissionCollection permissions)
	{
		//// TODO: cache
		synchronized (stmt)
		{
			try
			{
				stmt.setString(1, databaseName);
				stmt.setString(2, userName);
				stmt.setString(3, roleName);

				try (ResultSet rs = stmt.executeQuery())
				{
					while (rs.next())
					{
						try
						{
							Class<?> clazz = Class.forName(rs.getString(1));
							String arg1 = rs.getString(2);
							String arg2 = rs.getString(3);

							if (arg2 != null)
							{
								Constructor<?> constr = clazz.getDeclaredConstructor(
									String.class, String.class);
								permissions.add((Permission) constr.newInstance(arg1, arg2));
							}
							else if (arg1 != null)
							{
								Constructor<?> constr = clazz.getDeclaredConstructor(String.class);
								permissions.add((Permission) constr.newInstance(arg1));
							}
							else
							{
								Constructor<?> constr = clazz.getDeclaredConstructor();
								permissions.add((Permission) constr.newInstance());
							}
						}
						catch (Exception e)
						{
						}
					}
				}
			}
			catch (SQLException e)
			{
			}
		}
	}

	private static String windowsUpper(String s)
	{
		return IS_WINDOWS ? "upper(" + s + ")" : s;
	}
}
