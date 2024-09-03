package com.github.poliveira_fuze;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_SOURCES;

@Mojo(name = "avoid-star-import", defaultPhase = PROCESS_SOURCES)
public class AvoidStarImportMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}/src/main/java", property = "sourceDir", required = true)
  protected File sourceDir;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      Files.walk(sourceDir.toPath())
              .filter(path -> path.toString().endsWith(".java"))
              .forEach(path -> {
                try {
                  expandWildcardImports(path.toFile());
                } catch (IOException e) {
                  getLog().error("Error processing file: " + path, e);
                }
              });
    } catch (IOException e) {
      throw new MojoExecutionException("Error reading source directory", e);
    }
  }

  private void expandWildcardImports(File file) throws IOException {
    CompilationUnit compilationUnit;
    try (FileInputStream in = new FileInputStream(file)) {
      compilationUnit = StaticJavaParser.parse(in);
    }

    Set<String> usedTypes = new HashSet<>();
    compilationUnit.accept(new VoidVisitorAdapter<Void>() {
      @Override
      public void visit(ClassOrInterfaceDeclaration type, Void arg) {
        usedTypes.add(type.getNameAsString());
        super.visit(type, arg);
      }
    }, null);

    List<ImportDeclaration> imports = compilationUnit.getImports();
    for (ImportDeclaration imp : imports) {
      if (imp.isAsterisk()) {
        String packageName = imp.getNameAsString();

        for (String className : usedTypes) {
          try {
            Class<?> clazz = Class.forName(packageName + "." + className);
            compilationUnit.addImport(clazz);
          } catch (ClassNotFoundException e) {
            getLog().warn("Class not found: " + packageName + "." + className);
          }
        }
      }
    }

    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(compilationUnit.toString().getBytes());
    }

    getLog().info("Expanded wildcard imports in " + file);
  }
}
