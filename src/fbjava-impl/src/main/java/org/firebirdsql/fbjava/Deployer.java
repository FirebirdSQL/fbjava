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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.firebirdsql.fbjava.ExternalResultSet;


public class Deployer
{
	private boolean defaultConnection;
	private Connection conn;

	public Deployer(String[] args) throws Exception
	{
		defaultConnection = false;

		String database = null;
		String user = null;
		String password = null;

		for (int i = 0; i < args.length; ++i)
		{
			if (args[i] == null)
				continue;

			boolean moreArgs = (i + 1 < args.length && !args[i + 1].startsWith("-"));

			if (args[i].equals("--database"))
			{
				if (moreArgs)
					database = args[i + 1];
			}
			else if (args[i].equals("--user"))
			{
				if (moreArgs)
					user = args[i + 1];
			}
			else if (args[i].equals("--password"))
			{
				if (moreArgs)
					password = args[i + 1];
			}
			else
				continue;

			if (moreArgs)
			{
				args[i] = args[i + 1] = null;
				++i;
			}
			else
				throw new Exception("Missing argument for option " + args[i]);
		}

		if (database == null)
			throw new Exception("Option --database must be present");

		conn = DriverManager.getConnection("jdbc:firebirdsql:" + database, user, password);
		conn.setAutoCommit(false);
	}

	public Deployer() throws SQLException
	{
		conn = DriverManager.getConnection("jdbc:default:connection");
	}

	public void commit() throws SQLException
	{
		conn.commit();
	}

	public void close() throws SQLException
	{
		if (conn != null)
			conn.close();
	}

	private void runScript(String name) throws Exception
	{
		InputStream stream = getClass().getResource(name).openStream();
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

			Statement stmt = conn.createStatement();
			try
			{
				StringBuilder sb = new StringBuilder();
				String line;

				while ((line = reader.readLine()) != null)
				{
					sb.append(line);

					if (line.endsWith("!"))
					{
						String s = sb.substring(0, sb.length() - 1).trim();

						// Hack needed by the uninstall.sql script.
						if (s.startsWith("commit"))
							conn.commit();
						else
							stmt.execute(s);

						sb.setLength(0);
					}
					else
						sb.append("\r\n");
				}
			}
			finally
			{
				stmt.close();
			}
		}
		finally
		{
			stream.close();
		}
	}

	private void doInstallPlugin() throws Exception
	{
		runScript("install.sql");

		if (!defaultConnection)
			commit();
	}

	private void doUninstallPlugin() throws Exception
	{
		runScript("uninstall.sql");

		if (!defaultConnection)
			commit();
	}

	private class InstallJarResult implements ExternalResultSet
	{
		private String[] className;
		private PreparedStatement entryStmt;
		private JarInputStream jarStream;
		private byte[] buffer;

		public InstallJarResult(URL url, String name, String[] className) throws Exception
		{
			this.className = className;

			PreparedStatement jarStmt = conn.prepareStatement(
				"execute block (name type of column fb$java$jar.name = ?)\n" +
				"	returns (id type of column fb$java$jar.id)\n" +
				"as\n" +
				"begin\n" +
				"	insert into fb$java$jar (id, name, owner)\n" +
				"		values (next value for fb$java$seq, :name, current_user)\n" +
				"		returning id into id;\n" +
				"	suspend;\n" +
				"end");
			try
			{
				jarStmt.setString(1, name);
				ResultSet rs = jarStmt.executeQuery();
				rs.next();
				long jarId = rs.getLong(1);
				rs.close();

				//// TODO: manifest???
				entryStmt = conn.prepareStatement(
					"insert into fb$java$jar_entry (id, jar, name, content)\n" +
					"	values (next value for fb$java$seq, ?, ?, ?)");
				entryStmt.setLong(1, jarId);

				jarStream = new JarInputStream(url.openStream());
				buffer = new byte[8192];
			}
			finally
			{
				jarStmt.close();
			}
		}

		@Override
		public void close()
		{
			try
			{
				jarStream.close();
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean fetch() throws Exception
		{
			JarEntry entry;
			while ((entry = jarStream.getNextJarEntry()) != null && entry.isDirectory())
				;

			if (entry == null)
				return false;

			Blob blob = conn.createBlob();
			OutputStream out = blob.setBinaryStream(1);
			try
			{
				for (int count; (count = jarStream.read(buffer)) != -1; )
					out.write(buffer, 0, count);
			}
			finally
			{
				out.close();
			}

			className[0] = entry.getName();

			entryStmt.setString(2, className[0]);
			entryStmt.setBlob(3, blob);
			entryStmt.execute();

			jarStream.closeEntry();

			return true;
		}
	}

	private InstallJarResult doVerboseInstallJar(String url, String name, final String[] className,
		final boolean closeDeployer) throws Exception
	{
		if (url.indexOf("://") == -1)	// defaults to local files
			url = new File(url).toURI().toURL().toString();

		return new InstallJarResult(new URL(url), name, className) {
			@Override
			public boolean fetch() throws Exception
			{
				if (super.fetch())
					return true;
				else
				{
					if (closeDeployer)
						Deployer.this.close();
					return false;
				}
			}
		};
	}

	private void doRemoveJar(String name) throws Exception
	{
		PreparedStatement stmt = conn.prepareStatement(
			"execute procedure sqlj.remove_jar(?)");
		try
		{
			stmt.setString(1, name);
			stmt.execute();
		}
		finally
		{
			stmt.close();
		}
	}

	public static InstallJarResult verboseInstallJar(final String url, final String name,
		final String[] className) throws Exception
	{
		Deployer deployer = new Deployer();
		try
		{
			return deployer.doVerboseInstallJar(url, name, className, true);
		}
		catch (Exception e)
		{
			deployer.close();
			throw e;
		}
	}

	public void doMain(String args[]) throws Exception
	{
		for (int i = 0; i < args.length; ++i)
		{
			if (args[i] == null)
				continue;

			boolean moreArgs = (i + 1 < args.length && !args[i + 1].startsWith("-"));
			boolean more2Args = moreArgs && (i + 2 < args.length && !args[i + 2].startsWith("-"));
			boolean installPlugin = false;
			boolean uninstallPlugin = false;
			String installJar = null;
			String replaceJar = null;
			String removeJar = null;

			if (args[i].equals("--install-plugin"))
				installPlugin = true;
			else if (args[i].equals("--uninstall-plugin"))
				uninstallPlugin = true;
			else if (args[i].equals("--install-jar"))
			{
				if (more2Args)
					installJar = args[i + 1];
			}
			else if (args[i].equals("--replace-jar"))
			{
				if (more2Args)
					replaceJar = args[i + 1];
			}
			else if (args[i].equals("--remove-jar"))
			{
				if (moreArgs)
					removeJar = args[i + 1];
			}
			else
				throw new Exception("Invalid option " + args[i]);

			if (more2Args)
			{
				if (installJar != null)
				{
					String[] className = new String[1];
					InstallJarResult result = doVerboseInstallJar(args[i + 1], args[i + 2],
						className, false);

					while (result.fetch())
						;
				}
				else if (replaceJar != null)
				{
					doRemoveJar(args[i + 2]);

					String[] className = new String[1];
					InstallJarResult result = doVerboseInstallJar(args[i + 1], args[i + 2],
						className, false);

					while (result.fetch())
						;
				}

				i += 2;
			}
			else if (moreArgs)
			{
				if (removeJar != null)
					doRemoveJar(removeJar);

				++i;
			}
			else if (installPlugin)
				doInstallPlugin();
			else if (uninstallPlugin)
				doUninstallPlugin();
			else
				throw new Exception("Missing argument for option " + args[i]);
		}
	}

	public static void main(String[] args) throws Exception
	{
		Class.forName("org.firebirdsql.jdbc.FBDriver");

		if (args.length == 0)
		{
			System.out.println("Firebird/Java Deployer");
			System.out.println("Switches:");
			System.out.println("\t--database <database>\t\tDatabase connection string\n\t\t\t\t\t(e.g. embedded:database.fdb / server:database)");
			System.out.println("\t--user <username>\t\tUser name");
			System.out.println("\t--password <password>\t\tUser password");
			System.out.println("\t--install-plugin\t\tInstall the plugin on a database");
			System.out.println("\t--uninstall-plugin\t\tUninstall the plugin from a database");
			System.out.println("\t--install-jar <url> <name>\tInstall a JAR on a database");
			System.out.println("\t--replace-jar <url> <name>\tReplaces a JAR on a database");
			System.out.println("\t--remove-jar <name>\t\tRemoves a JAR from the database");
			return;
		}

		Deployer deployer = new Deployer(args);
		try
		{
			deployer.doMain(args);
			deployer.commit();
		}
		finally
		{
			deployer.close();
		}
	}
}
