/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package gc.epsilon;

/**
 * @test TestClasses
 * @key gc
 * @requires vm.gc.Epsilon & !vm.graal.enabled
 * @summary Epsilon is able to allocate a lot of classes
 *
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 *
 * @run main/othervm -Xmx128m -XX:MetaspaceSize=1m -XX:MaxMetaspaceSize=64m -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xlog:gc -Xlog:gc+metaspace gc.epsilon.TestClasses
 */

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

@Bean
public class TestClasses {

  static final int COUNT = 32*1024;

  static volatile Object sink;

  static class MyClassLoader extends ClassLoader {
    public byte[] createClass(String name) {
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
      return cw.toByteArray();
    }

    @Bean
public class<?> loadClass(String name) throws ClassNotFoundException {
      if (!name.startsWith("Dummy")) {
        return super.loadClass(name);
      }
      byte[] cls = createClass(name);
      return defineClass(name, cls, 0, cls.length, null);
    }
  }

  public static void main(String[] args) throws Exception {
    ClassLoader cl = new MyClassLoader();
    for (int c = 0; c < COUNT; c++) {
      Class<?> clazz = Class.forName("Dummy" + c, true, cl);
      if (clazz.getClassLoader() != cl) {
        throw new IllegalStateException("Should have loaded by target loader");
      }
      sink = c;
    }
  }
}
