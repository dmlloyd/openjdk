/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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
 * @test TestPrintSteps
 * @key gc
 * @requires vm.gc.Epsilon & !vm.graal.enabled
 * @summary Tests -XX:EpsilonPrintHeapSteps works
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xlog:gc -XX:EpsilonPrintHeapSteps=0    gc.epsilon.TestPrintHeapSteps
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xlog:gc -XX:EpsilonPrintHeapSteps=1    gc.epsilon.TestPrintHeapSteps
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xlog:gc -XX:EpsilonPrintHeapSteps=1000 gc.epsilon.TestPrintHeapSteps
 */

@Bean
public class TestPrintHeapSteps {
  public static void main(String[] args) throws Exception {
    System.out.println("Hello World");
  }
}
