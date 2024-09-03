package com.github.poliveira_fuze;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AvoidStarImportMojoTest {

  private AvoidStarImportMojo mojo;
  private File tempDir;

  @BeforeEach
  void setUp() throws IOException {
    mojo = new AvoidStarImportMojo();

    // Create a temporary directory for testing
    tempDir = Files.createTempDirectory("avoid-start-import-test").toFile();
    tempDir.deleteOnExit();

    // Set the source directory to the temporary directory
    mojo.sourceDir = tempDir;
  }

  @Test
  void testExpandWildcardImports() throws IOException, MojoExecutionException {
    // Create a mock Java file with wildcard import
    File testFile = new File(tempDir, "TestFile.java");
    try (FileWriter writer = new FileWriter(testFile)) {
      writer.write("import java.util.*;\n");
      writer.write("public class TestFile {\n");
      writer.write("    List<String> list = new ArrayList<>();\n");
      writer.write("}\n");
    }

    // Run the plugin
    mojo.execute();

    // Verify that the file has been modified correctly
    String content = new String(Files.readAllBytes(testFile.toPath()));

    assertTrue(content.contains("import java.util.List;"));
    assertTrue(content.contains("import java.util.ArrayList;"));
    assertFalse(content.contains("import java.util.*;"));
  }

  @Test
  void testNoWildcardImports() throws IOException, MojoExecutionException {
    // Create a mock Java file without wildcard import
    File testFile = new File(tempDir, "TestFileNoWildcard.java");
    try (FileWriter writer = new FileWriter(testFile)) {
      writer.write("import java.util.List;\n");
      writer.write("public class TestFileNoWildcard {\n");
      writer.write("    List<String> list;\n");
      writer.write("}\n");
    }

    // Run the plugin
    mojo.execute();

    // Verify that the file has not been modified
    String content = new String(Files.readAllBytes(testFile.toPath()));

    assertTrue(content.contains("import java.util.List;"));
    assertFalse(content.contains("import java.util.*;"));
  }

  @Test
  void testPluginLogs() throws IOException, MojoExecutionException {
    // Create a mock Java file with wildcard import
    File testFile = new File(tempDir, "TestFileWithLog.java");
    try (FileWriter writer = new FileWriter(testFile)) {
      writer.write("import java.util.*;\n");
      writer.write("public class TestFileWithLog {\n");
      writer.write("    List<String> list = new ArrayList<>();\n");
      writer.write("}\n");
    }

    // Spy on the mojo to mock the logging
    AvoidStarImportMojo spyMojo = Mockito.spy(mojo);
    doReturn(mock(org.apache.maven.plugin.logging.Log.class)).when(spyMojo).getLog(); // Correctly stub getLog()

    // Run the plugin
    spyMojo.execute();

    // Verify that the log was called with the correct message
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(spyMojo.getLog(), atLeastOnce()).info(captor.capture());

    assertTrue(captor.getAllValues().stream().anyMatch(msg -> msg.contains("Expanded wildcard imports in")));
  }
}
