package org.jax.octvolavg;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class SafeAWTInvoker {
    /**
     * No instances allowed
     */
    private SafeAWTInvoker() { }
    
    /**
     * This function delegate to {@link EventQueue#invokeLater(Runnable)} if
     * called outside of the event dispatching thread. If called from
     * the event dispatching thread {@link Runnable#run()} will be called
     * directly on the given runnable
     * @param runnable
     *          the runnable to call
     */
    public static void safeInvokeNowOrLater(Runnable runnable) {
        if(EventQueue.isDispatchThread()) {
            // since we're in the event queue already, we must invoke
            // the runnable directly
            runnable.run();
        } else {
            // delegate to the event queue
            EventQueue.invokeLater(runnable);
        }
    }

    /**
     * This function is basically the same as
     * {@link EventQueue#invokeAndWait(Runnable)} except that it is safe to call
     * from the event dispatching thread. If it is called from the event
     * dispatch thread is just executes right away.
     * @param runnable
     *          the runnable to call
     * @throws InvocationTargetException
     *          see {@link EventQueue#invokeAndWait(Runnable)} for a description
     *          of when this can happen
     * @throws InterruptedException
     *          see {@link EventQueue#invokeAndWait(Runnable)} for a description
     *          of when this can happen
     */
    public static void safeInvokeAndWait(Runnable runnable)
    throws InvocationTargetException, InterruptedException {
        
        if(EventQueue.isDispatchThread()) {
            // since we're in the event queue already, we must invoke
            // the runnable directly
            try {
                runnable.run();
            } catch(Exception ex) {
                throw new InvocationTargetException(ex);
            }
        } else {
            // delegate to the event queue
            EventQueue.invokeAndWait(runnable);
        }
    }
    
    /**
     * A call and wait function that is safe to call even if you are in the
     * AWT thread. This is similar to {@link #safeInvokeAndWait(Runnable)}
     * except that we want to return an actual result
     * @param <T>
     *          the type of result
     * @param callable
     *          the callable
     * @return
     *          the result
     * @throws ExecutionException
     *          if the callable throws an exception we'll wrap it up in this
     * @throws InterruptedException
     *          if there is a thread interruption when we
     *          {@link EventQueue#invokeLater(Runnable)}
     */
    public static <T> T safeCallAndWait(final Callable<T> callable)
    throws InterruptedException, ExecutionException {
        
        if(EventQueue.isDispatchThread()) {
            try {
                return callable.call();
            } catch(Exception ex) {
                throw new ExecutionException(ex);
            }
        } else {
            FutureTask<T> futureTask = new FutureTask<T>(callable);
            EventQueue.invokeLater(futureTask);
            
            // block until the computation is complete then return the
            // result
            return futureTask.get();
        }
    }
}
