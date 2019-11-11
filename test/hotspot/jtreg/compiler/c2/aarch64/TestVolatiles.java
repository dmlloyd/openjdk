/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
 * common code to run and validate tests of code generation for
 * volatile ops on AArch64
 *
 * incoming args are <testclass> <testtype>
 *
 * where <testclass> in {TestVolatileLoad,
 *                       TestVolatileStore,
 *                       TestUnsafeVolatileLoad,
 *                       TestUnsafeVolatileStore,
 *                       TestUnsafeVolatileCAS,
 *                       TestUnsafeVolatileWeakCAS,
 *                       TestUnsafeVolatileCAE,
 *                       TestUnsafeVolatileGAS}
 * and <testtype> in {G1,
 *                    CMS,
 *                    CMSCondMark,
 *                    Serial,
 *                    Parallel,
 *                    Shenandoah,
 *                    ShenandoahTraversal}
 */


package compiler.c2.aarch64;

import java.util.List;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.io.*;

import jdk.test.lib.Asserts;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import sun.hotspot.WhiteBox;

// runner class that spawns a new JVM to exercises a combination of
// volatile MemOp and GC. The ops are compiled with the dmb -->
// ldar/stlr transforms either enabled or disabled. this runner parses
// the PrintOptoAssembly output checking that the generated code is
// correct.

@Bean
public class TestVolatiles {
    public void runtest(String classname, String testType) throws Throwable {
        // n.b. clients omit the package name for the class
        String fullclassname = "compiler.c2.aarch64." + classname;
        // build up a command line for the spawned JVM
        String[] procArgs;
        int argcount;
        // add one or two extra arguments according to test type
        // i.e. GC type plus GC conifg
        switch(testType) {
        case "G1":
            argcount = 9;
            procArgs = new String[argcount];
            procArgs[argcount - 2] = "-XX:+UseG1GC";
            break;
        case "Parallel":
            argcount = 9;
            procArgs = new String[argcount];
            procArgs[argcount - 2] = "-XX:+UseParallelGC";
            break;
        case "Serial":
            argcount = 9;
            procArgs = new String[argcount];
            procArgs[argcount - 2] = "-XX:+UseSerialGC";
            break;
        case "CMS":
            argcount = 10;
            procArgs = new String[argcount];
            procArgs[argcount - 3] = "-XX:+UseConcMarkSweepGC";
            procArgs[argcount - 2] = "-XX:-UseCondCardMark";
            break;
        case "CMSCondMark":
            argcount = 10;
            procArgs = new String[argcount];
            procArgs[argcount - 3] = "-XX:+UseConcMarkSweepGC";
            procArgs[argcount - 2] = "-XX:+UseCondCardMark";
            break;
        case "Shenandoah":
            argcount = 10;
            procArgs = new String[argcount];
            procArgs[argcount - 3] = "-XX:+UnlockExperimentalVMOptions";
            procArgs[argcount - 2] = "-XX:+UseShenandoahGC";
            break;
        case "ShenandoahTraversal":
            argcount = 11;
            procArgs = new String[argcount];
            procArgs[argcount - 4] = "-XX:+UnlockExperimentalVMOptions";
            procArgs[argcount - 3] = "-XX:+UseShenandoahGC";
            procArgs[argcount - 2] = "-XX:ShenandoahGCMode=traversal";
            break;
        default:
            throw new RuntimeException("unexpected test type " + testType);
        }

        // fill in arguments common to all cases

        // the first round of test enables transform of barriers to
        // use acquiring loads and releasing stores by setting arg
        // zero appropriately. this arg is reset in the second run to
        // disable the transform.

        procArgs[0] = "-XX:-UseBarriersForVolatile";
        procArgs[1] = "-XX:+UseCompressedOops";

        procArgs[2] = "-XX:-TieredCompilation";
        procArgs[3] = "-XX:+PrintOptoAssembly";
        procArgs[4] = "-XX:CompileCommand=compileonly," + fullclassname + "::" + "test*";
        procArgs[5] = "--add-exports";
        procArgs[6] = "java.base/jdk.internal.misc=ALL-UNNAMED";
        procArgs[argcount - 1] = fullclassname;

        runtest(classname, testType, false, true, procArgs);
        // rerun the test class without the transform applied and
        // check the alternative generation is as expected

        procArgs[0] = "-XX:+UseBarriersForVolatile";
        runtest(classname, testType, true, true, procArgs);

        if (!classname.equals("TestUnsafeVolatileGAA")) {
            procArgs[0] = "-XX:-UseBarriersForVolatile";
            procArgs[1] = "-XX:-UseCompressedOops";
            runtest(classname, testType, false, false, procArgs);

            procArgs[0] = "-XX:+UseBarriersForVolatile";
            runtest(classname, testType, true, false, procArgs);
        }
    }


    public void runtest(String classname, String testType, boolean useBarriersForVolatile, boolean useCompressedOops, String[] procArgs) throws Throwable {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.stderrShouldBeEmptyIgnoreVMWarnings();
        output.stdoutShouldNotBeEmpty();
        output.shouldHaveExitValue(0);

        // check the output for the correct asm sequence as
        // appropriate to test class, test type and whether transform
        // was applied

        checkoutput(output, classname, testType, useBarriersForVolatile, useCompressedOops);
    }

    // skip through output returning a line containing the desireed
    // substring or null
    private String skipTo(Iterator<String> iter, String substring)
    {
        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.matches(".*" + substring + ".*")) {
                return nextLine;
            }
        }
        return null;
    }

    // locate the start of compiler output for the desired method and
    // then check that each expected instruction occurs in the output
    // in the order supplied. throw an excpetion if not found.
    // n.b. the spawned JVM's output is included in the exception
    // message to make it easeir to identify what is missing.

    private boolean checkCompile(Iterator<String> iter, String methodname, String[] expected, OutputAnalyzer output, boolean do_throw)
    {
        // trace call to allow eyeball check of what we are checking against
        System.out.println("checkCompile(" + methodname + ",");
        String sepr = "  { ";
        for (String s : expected) {
            System.out.print(sepr);
            System.out.print(s);
            sepr = ",\n    ";
        }
        System.out.println(" })");

        // look for the start of an opto assembly print block
        String match = skipTo(iter, Pattern.quote("{method}"));
        if (match == null) {
            if (do_throw) {
                throw new RuntimeException("Missing compiler output for " + methodname + "!\n\n" + output.getOutput());
            }
            return false;
        }
        // check the compiled method name is right
        match = skipTo(iter, Pattern.quote("- name:"));
        if (match == null) {
            if (do_throw) {
                throw new RuntimeException("Missing compiled method name!\n\n" + output.getOutput());
            }
            return false;
        }
        if (!match.contains(methodname)) {
            if (do_throw) {
                throw new RuntimeException("Wrong method " + match + "!\n  -- expecting " + methodname + "\n\n" + output.getOutput());
            }
            return false;
        }
        // make sure we can match each expected term in order
        for (String s : expected) {
            match = skipTo(iter, s);
            if (match == null) {
                if (do_throw) {
                    throw new RuntimeException("Missing expected output " + s + "!\n\n" + output.getOutput());
                }
                return false;
            }
        }
        return true;
    }

    // check for expected asm output from a volatile load

    private void checkload(OutputAnalyzer output, String testType, boolean useBarriersForVolatile, boolean useCompressedOops) throws Throwable
    {
        Iterator<String> iter = output.asLines().listIterator();

        // we shoud see this same sequence for normal or unsafe volatile load
        // for both int and Object fields

        String[] matches;

        if (!useBarriersForVolatile) {
            matches = new String[] {
                "ldarw",
                "membar_acquire \\(elided\\)",
                "ret"
            };
        } else {
            matches = new String[] {
                "ldrw",
                "membar_acquire",
                "dmb ish",
                "ret"
            };
        }

        checkCompile(iter, "testInt", matches, output, true);

        if (!useBarriersForVolatile) {
            matches = new String[] {
                useCompressedOops ? "ldarw?" : "ldar",
                "membar_acquire \\(elided\\)",
                "ret"
            };
        } else {
            matches = new String[] {
                useCompressedOops ? "ldrw?" : "ldr",
                "membar_acquire",
                "dmb ish",
                "ret"
            };
        }

        checkCompile(iter, "testObj", matches, output, true);

    }

    // check for expected asm output from a volatile store

    private void checkstore(OutputAnalyzer output, String testType, boolean useBarriersForVolatile, boolean useCompressedOops) throws Throwable
    {
        Iterator<String> iter = output.asLines().listIterator();

        String[] matches;

        // non object stores are straightforward
        if (!useBarriersForVolatile) {
            // this is the sequence of instructions for all cases
            matches = new String[] {
                "membar_release \\(elided\\)",
                "stlrw",
                "membar_volatile \\(elided\\)",
                "ret"
            };
        } else {
            // this is the alternative sequence of instructions
            matches = new String[] {
                "membar_release",
                "dmb ish",
                "strw",
                "membar_volatile",
                "dmb ish",
                "ret"
            };
        }

        checkCompile(iter, "testInt", matches, output, true);

        // object stores will be as above except for when the GC
        // introduces barriers for card marking

        if (!useBarriersForVolatile) {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "stlrw?" : "stlr",
                    "membar_volatile \\(elided\\)",
                    "ret"
                };
                break;
            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "stlrw?" : "stlr",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_volatile \\(elided\\)",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "stlrw?" : "stlr",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_volatile \\(elided\\)",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be
                // generated as "dmb ishst"
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "stlrw?" : "stlr",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_volatile \\(elided\\)",
                    "ret"
                };
                break;
            case "Shenandoah":
            case "ShenandoahTraversal":
                 // Shenandoah generates normal object graphs for
                 // volatile stores
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "stlrw?" : "stlr",
                    "membar_volatile \\(elided\\)",
                    "ret"
                };
                break;
            }
        } else {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "strw?" : "str",
                    "membar_volatile",
                    "dmb ish",
                    "ret"
                };
                break;
            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "strw?" : "str",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_volatile",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "strw?" : "str",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_volatile",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be generated
                // as "dmb ishst"
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "strw?" : "str",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_volatile",
                    "dmb ish",
                    "ret"
                };
                break;

            case "Shenandoah":
            case "ShenandoahTraversal":
                 // Shenandoah generates normal object graphs for
                 // volatile stores
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "strw?" : "str",
                    "membar_volatile",
                    "dmb ish",
                    "ret"
                };
                break;
            }
        }

        checkCompile(iter, "testObj", matches, output, true);
    }

    // check for expected asm output from a volatile cas

    private void checkcas(OutputAnalyzer output, String testType, boolean useBarriersForVolatile, boolean useCompressedOops) throws Throwable
    {
        Iterator<String> iter = output.asLines().listIterator();

        String[] matches;
        String[][] tests = {
            { "testInt", "cmpxchgw" },
            { "testLong", "cmpxchg" },
            { "testByte", "cmpxchgb" },
            { "testShort", "cmpxchgs" },
        };

        for (String[] test : tests) {
            // non object stores are straightforward
            if (!useBarriersForVolatile) {
                // this is the sequence of instructions for all cases
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    test[1] + "_acq",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
            } else {
                // this is the alternative sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    test[1] + " ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
            }

            checkCompile(iter, test[0], matches, output, true);
        }

        // object stores will be as above except for when the GC
        // introduces barriers for card marking

        if (!useBarriersForVolatile) {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "Shenandoah":
            case "ShenandoahTraversal":
                // For volatile CAS, Shenanodoah generates normal
                // graphs with a shenandoah-specific cmpxchg
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq_shenandoah" : "cmpxchg_acq_shenandoah",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            }
        } else {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be generated
                // as "dmb ishst"
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "Shenandoah":
            case "ShenandoahTraversal":
                // For volatile CAS, Shenanodoah generates normal
                // graphs with a shenandoah-specific cmpxchg
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw?_shenandoah" : "cmpxchg_shenandoah",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            }
        }

        checkCompile(iter, "testObj", matches, output, true);
    }

    private void checkcae(OutputAnalyzer output, String testType, boolean useBarriersForVolatile, boolean useCompressedOops) throws Throwable
    {
        ListIterator<String> iter = output.asLines().listIterator();

        String[] matches;
        String[][] tests = {
            { "testInt", "cmpxchgw" },
            { "testLong", "cmpxchg" },
            { "testByte", "cmpxchgb" },
            { "testShort", "cmpxchgs" },
        };

        for (String[] test : tests) {
            // non object stores are straightforward
            if (!useBarriersForVolatile) {
                // this is the sequence of instructions for all cases
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    test[1] + "_acq",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
            } else {
                // this is the alternative sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    test[1] + " ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
            }

            checkCompile(iter, test[0], matches, output, true);
        }

        // object stores will be as above except for when the GC
        // introduces barriers for card marking

        if (!useBarriersForVolatile) {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    "strb",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };

                // card marking store may be scheduled before or after
                // the cmpxchg so try both sequences.
                int idx = iter.nextIndex();
                if (!checkCompile(iter, "testObj", matches, output, false)) {
                    iter = output.asLines().listIterator(idx);

                    matches = new String[] {
                        "membar_release \\(elided\\)",
                        useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                        "strb",
                        "membar_acquire \\(elided\\)",
                        "ret"
                    };

                    checkCompile(iter, "testObj", matches, output, true);
                }
                return;

            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq" : "cmpxchg_acq",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "Shenandoah":
            case "ShenandoahTraversal":
                // For volatile CAS, Shenanodoah generates normal
                // graphs with a shenandoah-specific cmpxchg
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "cmpxchgw?_acq_shenandoah" : "cmpxchg_acq_shenandoah",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            }
        } else {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be generated
                // as "dmb ishst"
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw? " : "cmpxchg ",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "Shenandoah":
            case "ShenandoahTraversal":
                // For volatile CAS, Shenanodoah generates normal
                // graphs with a shenandoah-specific cmpxchg
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "cmpxchgw?_shenandoah" : "cmpxchg_shenandoah",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            }
        }

        checkCompile(iter, "testObj", matches, output, true);
    }

    private void checkgas(OutputAnalyzer output, String testType, boolean useBarriersForVolatile, boolean useCompressedOops) throws Throwable
    {
        Iterator<String> iter = output.asLines().listIterator();

        String[] matches;
        String[][] tests = {
            { "testInt", "atomic_xchgw" },
            { "testLong", "atomic_xchg" },
        };

        for (String[] test : tests) {
            // non object stores are straightforward
            if (!useBarriersForVolatile) {
                // this is the sequence of instructions for all cases
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    test[1] + "_acq",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
            } else {
                // this is the alternative sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    test[1] + " ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
            }

            checkCompile(iter, test[0], matches, output, true);
        }

        // object stores will be as above except for when the GC
        // introduces barriers for card marking

        if (!useBarriersForVolatile) {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "atomic_xchgw?_acq" : "atomic_xchg_acq",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "atomic_xchgw?_acq" : "atomic_xchg_acq",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "atomic_xchgw?_acq" : "atomic_xchg_acq",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "atomic_xchgw?_acq" : "atomic_xchg_acq",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            case "Shenandoah":
            case "ShenandoahTraversal":
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    useCompressedOops ? "atomic_xchgw?_acq" : "atomic_xchg_acq",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
                break;
            }
        } else {
            switch (testType) {
            default:
                // this is the basic sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "atomic_xchgw? " : "atomic_xchg ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "G1":
                // a card mark volatile barrier should be generated
                // before the card mark strb
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "atomic_xchgw? " : "atomic_xchg ",
                    "membar_volatile",
                    "dmb ish",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMSCondMark":
                // a card mark volatile barrier should be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be elided
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "atomic_xchgw? " : "atomic_xchg ",
                    "membar_volatile",
                    "dmb ish",
                    "storestore \\(elided\\)",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "CMS":
                // a volatile card mark membar should not be generated
                // before the card mark strb from the StoreCM and the
                // storestore barrier from the StoreCM should be generated
                // as "dmb ishst"
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "atomic_xchgw? " : "atomic_xchg ",
                    "storestore",
                    "dmb ishst",
                    "strb",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            case "Shenandoah":
            case "ShenandoahTraversal":
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    useCompressedOops ? "atomic_xchgw? " : "atomic_xchg ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
                break;
            }
        }

        checkCompile(iter, "testObj", matches, output, true);
    }

    private void checkgaa(OutputAnalyzer output, String testType, boolean useBarriersForVolatile) throws Throwable
    {
        Iterator<String> iter = output.asLines().listIterator();

        String[] matches;
        String[][] tests = {
            { "testInt", "get_and_addI" },
            { "testLong", "get_and_addL" },
        };

        for (String[] test : tests) {
            // non object stores are straightforward
            if (!useBarriersForVolatile) {
                // this is the sequence of instructions for all cases
                matches = new String[] {
                    "membar_release \\(elided\\)",
                    test[1] + "_acq",
                    "membar_acquire \\(elided\\)",
                    "ret"
                };
            } else {
                // this is the alternative sequence of instructions
                matches = new String[] {
                    "membar_release",
                    "dmb ish",
                    test[1] + " ",
                    "membar_acquire",
                    "dmb ish",
                    "ret"
                };
            }

            checkCompile(iter, test[0], matches, output, true);
        }

    }

    // perform a check appropriate to the classname

    private void checkoutput(OutputAnalyzer output, String classname, String testType, boolean useBarriersForVolatile, boolean useCompressedOops) throws Throwable
    {
        // trace call to allow eyeball check of what is being checked
        System.out.println("checkoutput(" +
                           classname + ", " +
                           testType + ", " +
                           useBarriersForVolatile + ")\n" +
                           output.getOutput());

        switch (classname) {
        case "TestVolatileLoad":
            checkload(output, testType, useBarriersForVolatile, useCompressedOops);
            break;
        case "TestVolatileStore":
            checkstore(output, testType, useBarriersForVolatile, useCompressedOops);
            break;
        case "TestUnsafeVolatileLoad":
            checkload(output, testType, useBarriersForVolatile, useCompressedOops);
            break;
        case "TestUnsafeVolatileStore":
            checkstore(output, testType, useBarriersForVolatile, useCompressedOops);
            break;
        case "TestUnsafeVolatileCAS":
        case "TestUnsafeVolatileWeakCAS":
            checkcas(output, testType, useBarriersForVolatile, useCompressedOops);
            break;
        case "TestUnsafeVolatileCAE":
            checkcae(output, testType, useBarriersForVolatile, useCompressedOops);
            break;
        case "TestUnsafeVolatileGAS":
            checkgas(output, testType, useBarriersForVolatile, useCompressedOops);
            break;
        case "TestUnsafeVolatileGAA":
            checkgaa(output, testType, useBarriersForVolatile);
            break;
        }
    }
}
