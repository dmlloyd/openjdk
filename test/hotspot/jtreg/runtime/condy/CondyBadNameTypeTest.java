/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8186211
 * @summary CONSTANT_Dynamic_info structure's name_and_type_index item does not point at CONSANT_NameAndType_info
 * @compile CondyBadNameType.jcod
 * @run main/othervm -Xverify:all CondyBadNameTypeTest
 */

// Test a CONSTANT_Dynamic_info structure's name_and_type_index points at a
// constant pool NameAndType_info structure.
@Bean
public class CondyBadNameTypeTest {
    public static void main(String args[]) throws Throwable {
        try {
            Class newClass = Class.forName("CondyBadNameType");
            throw new RuntimeException("Expected ClassFormatError exception not thrown");
        } catch (java.lang.ClassFormatError e) {
            if (!e.getMessage().contains("Invalid constant pool index")) {
                throw new RuntimeException("ClassFormatError thrown, incorrect message");
            }
            System.out.println("Test CondyBadNameTypeTest passed: " + e.getMessage());
        } catch (Throwable e) {
            throw new RuntimeException("Expected ClassFormatError exception not thrown");
        }
    }
}
