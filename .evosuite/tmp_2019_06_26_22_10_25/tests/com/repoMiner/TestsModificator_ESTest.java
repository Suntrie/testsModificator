/*
 * This file was automatically generated by EvoSuite
 * Wed Jun 26 19:12:13 GMT 2019
 */

package com.repoMiner;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.evosuite.runtime.EvoAssertions.*;
import com.repoMiner.TestsModificator;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.evosuite.runtime.EvoRunner;
import org.evosuite.runtime.EvoRunnerParameters;
import org.evosuite.runtime.testdata.EvoSuiteFile;
import org.evosuite.runtime.testdata.FileSystemHandling;
import org.junit.runner.RunWith;

@RunWith(EvoRunner.class) @EvoRunnerParameters(mockJVMNonDeterminism = true, useVFS = true, useVNET = true, resetStaticState = true, separateClassLoader = true, useJEE = true) 
public class TestsModificator_ESTest extends TestsModificator_ESTest_scaffolding {

  @Test(timeout = 4000)
  public void test0()  throws Throwable  {
      EvoSuiteFile evoSuiteFile0 = new EvoSuiteFile("/pom.xml");
      byte[] byteArray0 = new byte[4];
      FileSystemHandling.appendDataToFile(evoSuiteFile0, byteArray0);
      TestsModificator testsModificator0 = null;
      try {
        testsModificator0 = new TestsModificator((String) null, (String) null);
        fail("Expecting exception: XmlPullParserException");
      
      } catch(Throwable e) {
         //
         // only whitespace content allowed before start tag and not \\u0 (position: START_DOCUMENT seen \\u0... @1:1) 
         //
         verifyException("org.codehaus.plexus.util.xml.pull.MXParser", e);
      }
  }

  @Test(timeout = 4000)
  public void test1()  throws Throwable  {
      EvoSuiteFile evoSuiteFile0 = new EvoSuiteFile("/pom.xml");
      byte[] byteArray0 = new byte[9];
      FileSystemHandling.appendDataToFile(evoSuiteFile0, byteArray0);
      FileSystemHandling.shouldAllThrowIOExceptions();
      TestsModificator testsModificator0 = null;
      try {
        testsModificator0 = new TestsModificator("", "_");
        fail("Expecting exception: IOException");
      
      } catch(Throwable e) {
         //
         // Simulated IOException
         //
         verifyException("org.evosuite.runtime.vfs.VirtualFileSystem", e);
      }
  }

  @Test(timeout = 4000)
  public void test2()  throws Throwable  {
      TestsModificator testsModificator0 = null;
      try {
        testsModificator0 = new TestsModificator("", "");
        fail("Expecting exception: FileNotFoundException");
      
      } catch(Throwable e) {
         //
         // no message in exception (getMessage() returned null)
         //
         verifyException("org.evosuite.runtime.mock.java.io.MockFileInputStream", e);
      }
  }

  @Test(timeout = 4000)
  public void test3()  throws Throwable  {
      EvoSuiteFile evoSuiteFile0 = new EvoSuiteFile("/pom.xml");
      FileSystemHandling.appendStringToFile(evoSuiteFile0, "");
      TestsModificator testsModificator0 = null;
      try {
        testsModificator0 = new TestsModificator((String) null, (String) null);
        fail("Expecting exception: EOFException");
      
      } catch(Throwable e) {
         //
         // input contained no data
         //
         verifyException("org.codehaus.plexus.util.xml.pull.MXParser", e);
      }
  }
}
