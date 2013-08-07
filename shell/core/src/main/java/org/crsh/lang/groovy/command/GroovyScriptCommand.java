/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.crsh.lang.groovy.command;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.crsh.cli.impl.completion.CompletionMatch;
import org.crsh.cli.impl.Delimiter;
import org.crsh.cli.spi.Completion;
import org.crsh.command.CommandContext;
import org.crsh.command.CommandInvoker;
import org.crsh.command.DescriptionFormat;
import org.crsh.command.InvocationContext;
import org.crsh.command.InvocationContextImpl;
import org.crsh.command.NoSuchCommandException;
import org.crsh.command.RuntimeContext;
import org.crsh.command.ScriptException;
import org.crsh.command.ShellCommand;
import org.crsh.lang.groovy.closure.PipeLineClosure;
import org.crsh.lang.groovy.closure.PipeLineInvoker;
import org.crsh.shell.impl.command.CRaSH;
import org.crsh.text.RenderPrintWriter;
import org.crsh.util.Strings;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class GroovyScriptCommand extends Script implements ShellCommand {

  /** . */
  private LinkedList<InvocationContext<?>> stack;

  /** The current context. */
  protected InvocationContext context;

  /** The current output. */
  protected RenderPrintWriter out;

  /** . */
  private String[] args;

  protected GroovyScriptCommand() {
    this.stack = null;
  }

  public final void pushContext(InvocationContext<?> context) throws NullPointerException {
    if (context == null) {
      throw new NullPointerException();
    }

    //
    if (stack == null) {
      stack = new LinkedList<InvocationContext<?>>();
    }

    // Save current context (is null the first time)
    stack.addLast((InvocationContext)this.context);

    // Set new context
    this.context = context;
    this.out = context.getWriter();
  }

  public final InvocationContext<?> popContext() {
    if (stack == null || stack.isEmpty()) {
      throw new IllegalStateException("Cannot pop a context anymore from the stack");
    }
    InvocationContext context = this.context;
    this.context = stack.removeLast();
    this.out = this.context != null ? this.context.getWriter() : null;
    return context;
  }

  public final void execute(String s) throws ScriptException, IOException {
    InvocationContext<?> context = peekContext();
    CommandInvoker invoker = context.resolve(s);
    invoker.invoke(context);
  }

  public final InvocationContext<?> peekContext() {
    return (InvocationContext<?>)context;
  }

  @Override
  public final Object invokeMethod(String name, Object args) {

    //
    try {
      return super.invokeMethod(name, args);
    }
    catch (MissingMethodException missing) {
      if (context instanceof InvocationContext) {
        CRaSH crash = (CRaSH)context.getSession().get("crash");
        if (crash != null) {
          ShellCommand cmd;
          try {
            cmd = crash.getCommand(name);
          }
          catch (NoSuchCommandException ce) {
            throw new InvokerInvocationException(ce);
          }
          if (cmd != null) {
            InvocationContext<Object> ic = (InvocationContext<Object>)peekContext();
            PipeLineClosure closure = new PipeLineClosure(ic, name, cmd);
            PipeLineInvoker evaluation = closure.bind(args);
            try {
              evaluation.invoke(ic);
              return null;
            }
            catch (IOException e) {
              throw new GroovyRuntimeException(e);
            }
            catch (UndeclaredThrowableException e) {
              throw new GroovyRuntimeException(e.getCause());
            }
          }
        }
      }

      //
      throw missing;
    }
  }

  @Override
  public final Object getProperty(String property) {
    if ("out".equals(property)) {
      if (context instanceof InvocationContext<?>) {
        return ((InvocationContext<?>)context).getWriter();
      } else {
        return null;
      }
    } else if ("context".equals(property)) {
      return context;
    } else {
      if (context instanceof InvocationContext<?>) {
        CRaSH crash = (CRaSH)context.getSession().get("crash");
        if (crash != null) {
          try {
            ShellCommand cmd = crash.getCommand(property);
            if (cmd != null) {
              InvocationContext<Object> ic = (InvocationContext<Object>)peekContext();
              return new PipeLineClosure(ic, property, cmd);
            }
          } catch (NoSuchCommandException e) {
            throw new InvokerInvocationException(e);
          }
        }
      }

      //
      try {
        return super.getProperty(property);
      }
      catch (MissingPropertyException e) {
        return null;
      }
    }
  }

  public final CompletionMatch complete(RuntimeContext context, String line) {
    return new CompletionMatch(Delimiter.EMPTY, Completion.create());
  }

  public final String describe(String line, DescriptionFormat mode) {
    return null;
  }

  private final CommandInvoker<Object, Object> invoker = new CommandInvoker<Object, Object>() {

    public final Class<Object> getProducedType() {
      return Object.class;
    }

    public final Class<Object> getConsumedType() {
      return Object.class;
    }

    public void open(CommandContext<? super Object> consumer) {
      // Set up current binding
      Binding binding = new Binding(consumer.getSession());

      // Set the args on the script
      binding.setProperty("args", args);

      //
      setBinding(binding);

      //
      pushContext(new InvocationContextImpl<Object>((CommandContext<Object>)consumer));

      //
      try {
        //
        Object res = run();

        // Evaluate the closure
        if (res instanceof Closure) {
          Closure closure = (Closure)res;
          res = closure.call(args);
        }

        //
        if (res != null) {
          RenderPrintWriter writer = peekContext().getWriter();
          if (writer.isEmpty()) {
            writer.print(res);
          }
        }
      }
      catch (Exception t) {
        throw GroovyCommand.unwrap(t);
      }
    }

    public void provide(Object element) throws IOException {
      // Should never be called
    }

    public void flush() throws IOException {
      peekContext().flush();
    }

    public void close() throws IOException, UndeclaredThrowableException {
      popContext();
    }
  };

  public final CommandInvoker<?, ?> resolveInvoker(String line) {
    List<String> chunks = Strings.chunks(line);
    this.args = chunks.toArray(new String[chunks.size()]);
    return invoker;
  }

  public final CommandInvoker<?, ?> resolveInvoker(String name, Map<String, ?> options, List<?> args) {
    String[] tmp = new String[args.size()];
    for (int i = 0;i < tmp.length;i++) {
      tmp[i] = args.get(i).toString();
    }
    this.args = tmp;
    return invoker;
  }
}
