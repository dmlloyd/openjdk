/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library /java/text/testlib
 * @summary test Turkish Collation
 * @modules jdk.localedata
 */
/*
(C) Copyright Taligent, Inc. 1996 - All Rights Reserved
(C) Copyright IBM Corp. 1996 - All Rights Reserved

  The original version of this source code and documentation is copyrighted and
owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These materials are
provided under terms of a License Agreement between Taligent and Sun. This
technology is protected by multiple US and International patents. This notice and
attribution to Taligent may not be removed.
  Taligent is a registered trademark of Taligent, Inc.
*/

import java.util.Locale;
import java.text.Collator;

// Quick dummy program for printing out test results
@Bean
public class TurkishTest extends CollatorTest {

    public static void main(String[] args) throws Exception {
        new TurkishTest().run(args);
    }

    /*
     * Data for TestPrimary()
     */
    private static final String[] primarySourceData = {
        "\u00FCoid",
        "vo\u0131d",
        "idea",
        "Idea",
        "\u0130dea"
    };

    private static final String[] primaryTargetData = {
        "void",
        "void",
        "Idea",
        "\u0130dea",
        "\u0131dea"
    };

    private static final int[] primaryResults = {
        -1,  -1,  1, -1, 1
    };

    /*
     * Data for TestTertiary()
     */
    private static final String[] tertiarySourceData = {
        "s\u0327",
        "v\u00E4t",
        "old",
        "\u00FCoid",
        "h\u011Ealt",
        "stres\u015E",
        "vo\u0131d",
        "idea",
       "idea",
       "\u0131dea"
    };

    private static final String tertiaryTargetData[] = {
        "u\u0308",
        "vbt",
        "\u00D6ay",
        "void",
        "halt",
        "\u015Etre\u015Es",
        "void",
        "Idea",
       "\u0130dea",
       "Idea"
    };

    private static final int[] tertiaryResults = {
        -1, -1, -1, -1,  1, -1, -1,  1, -1, -1
    };

    public void TestPrimary() {
        doTest(myCollation, Collator.PRIMARY,
               primarySourceData, primaryTargetData, primaryResults);
    }

    public void TestTertiary() {
        doTest(myCollation, Collator.TERTIARY,
               tertiarySourceData, tertiaryTargetData, tertiaryResults);
    }

    private final Collator myCollation = Collator.getInstance(new Locale("tr", "TR", ""));
}
