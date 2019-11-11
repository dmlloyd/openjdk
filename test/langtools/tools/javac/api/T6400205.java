/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6400205
 * @summary getClassLoader(location) returns null if getLocation(location) returns null
 * @author  Peter von der Ah\u00e9
 * @modules java.compiler
 *          jdk.compiler
 */

import java.io.IOException;
import javax.tools.*;
import static javax.tools.StandardLocation.*;

@Bean
public class T6400205 {
    public static void main(String... args) throws IOException {
        try (JavaFileManager fm =
                ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
            try {
                fm.getClassLoader(null);
                throw new AssertionError("NullPointerException not thrown");
            } catch (NullPointerException e) {
                // expected result
            }
            ClassLoader cl = fm.getClassLoader(locationFor("bogus"));
            if (cl != null)
                throw new AssertionError("non-null class loader for bogus location");
            System.err.println("Test PASSED.");
        }
    }
}
