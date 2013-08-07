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
package org.crsh.repl;

import org.crsh.cli.Command;
import org.crsh.command.CRaSHCommand;
import org.crsh.shell.AbstractCommandTestCase;
import org.crsh.shell.Commands;

import java.util.ArrayList;
import java.util.Arrays;

/** @author Julien Viet */
public class REPLTestCase extends AbstractCommandTestCase {

  /** . */
  public static final ArrayList<Object> list = new ArrayList<Object>();

  public void testResolveContext() {
    Object context = groovyShell.evaluate("context");
    assertNotNull(context);
  }

  public void testClosure() {
    lifeCycle.bind("produce", Commands.ProduceString.class);
    assertOk("repl groovy");
    list.clear();
    assertOk("produce() { it -> " + REPLTestCase.class.getName() + ".list << it } ");
    assertEquals(Arrays.<Object>asList("foo", "bar"), list);
  }

  public void testResolveContextInClosure() {
    lifeCycle.bind("produce", Commands.ProduceString.class);
    assertOk("repl groovy");
    String result = assertOk("produce() { String it -> context.provide(it) } ");
    assertEquals("foobar", result);
  }

  public void testReturnValueInClosure() {
    lifeCycle.bind("produce", Commands.ProduceString.class);
    assertOk("repl groovy");
    String result = assertOk("produce() { String it -> it } ");
    assertEquals("foobar", result);
  }

  public void testClosureInPipe() {
    lifeCycle.bind("produce", Commands.ProduceString.class);
    lifeCycle.bind("consume", Commands.ConsumeString.class);
    assertOk("repl groovy");
    Commands.list.clear();
    assertOk("(produce | { String it -> '_' + it + '_' } | consume)()");
    assertEquals(Arrays.<Object>asList("_foo_", "_bar_"), Commands.list);
  }

  public static class Toto extends CRaSHCommand {
    @Command
    public String sub() {
      return "invoked";
    }
  }

  public void testSubCommand() {
    lifeCycle.bind("toto", Toto.class);
    assertOk("repl groovy");
    String result = assertOk("toto.sub()");
    assertEquals("invoked", result);
  }

  public void testProvideToContext() {
    assertOk("repl groovy");
    String result = assertOk("context << 'hello'");
    assertTrue(result.startsWith("hello"));
  }

  public void testPipe() {
    lifeCycle.bind("produce", Commands.ProduceString.class);
    lifeCycle.bind("consume", Commands.ConsumeString.class);
    assertOk("repl groovy");
    Commands.list.clear();
    assertOk("(produce | consume)()");
    assertEquals(Arrays.<Object>asList("foo", "bar"), Commands.list);
  }

  public void testOptions() {
    lifeCycle.bind("parameterized", Commands.Parameterized.class);
    assertOk("repl groovy");
    Commands.Parameterized.reset();
    assertOk("a = parameterized.with(opt:'foo_opt')");
    assertEquals(null, Commands.Parameterized.opt);
    assertEquals(null, Commands.Parameterized.args);
    assertOk("a()");
    assertEquals("foo_opt", Commands.Parameterized.opt);
    assertEquals(null, Commands.Parameterized.args);
  }

  public void testArgs() {
    lifeCycle.bind("parameterized", Commands.Parameterized.class);
    assertOk("repl groovy");
    Commands.Parameterized.reset();
    assertOk("a = parameterized.with('arg1', 'arg2')");
    assertEquals(null, Commands.Parameterized.opt);
    assertEquals(null, Commands.Parameterized.args);
    assertOk("a()");
    assertEquals(null, Commands.Parameterized.opt);
    assertEquals(Arrays.asList("arg1", "arg2"), Commands.Parameterized.args);
  }

  public void testOptionsAndArgs() {
    lifeCycle.bind("parameterized", Commands.Parameterized.class);
    assertOk("repl groovy");
    Commands.Parameterized.reset();
    assertOk("a = parameterized.with(opt:'foo_opt', 'arg1', 'arg2')");
    assertEquals(null, Commands.Parameterized.opt);
    assertEquals(null, Commands.Parameterized.args);
    assertOk("a()");
    assertEquals("foo_opt", Commands.Parameterized.opt);
    assertEquals(Arrays.asList("arg1", "arg2"), Commands.Parameterized.args);
  }
}
