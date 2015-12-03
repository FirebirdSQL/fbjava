package org.firebirdsql.fbjava;

import java.lang.reflect.InvocationTargetException;

import org.firebirdsql.fbjava.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalFunctionIntf;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


class ExternalFunction implements IExternalFunctionIntf
{
	private Routine routine;

	public ExternalFunction(Routine routine)
	{
		this.routine = routine;
	}

	@Override
	public void dispose()
	{
		//// TODO:
	}

	@Override
	public void getCharSet(IStatus status, IExternalContext context, Pointer name, int nameSize) throws FbException
	{
		name.setString(0, "UTF8");
	}

	@Override
	public void execute(IStatus status, IExternalContext context, Pointer inMsg, Pointer outMsg) throws FbException
	{
		try
		{
			Object[] in = routine.getFromMessage(status, routine.inputParameters, inMsg);
			Object[] out = {routine.method.invoke(null, in)};

			routine.putInMessage(status, routine.outputParameters, out, outMsg);
		}
		catch (InvocationTargetException e)
		{
			FbException.rethrow(e.getCause());
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
		}
	}
}
