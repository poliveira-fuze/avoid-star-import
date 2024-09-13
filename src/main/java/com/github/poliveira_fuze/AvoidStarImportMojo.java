package com.github.poliveira_fuze;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.SneakyThrows;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_SOURCES;

@Mojo(name = "avoid-star-import", defaultPhase = PROCESS_SOURCES)
public class AvoidStarImportMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}/src/main/java", property = "sourceDir", required = true)
  protected File sourceDir;

  @Override
  @SneakyThrows
  public void execute() {
    try {
      Files.walk(sourceDir.toPath())
              .filter(path -> path.toString().endsWith(".java"))
              .forEach(path -> expandWildcardImports(path.toFile()));
    } catch (IOException e) {
      throw new MojoExecutionException("Error reading source directory", e);
    }
  }

  @SneakyThrows
  private CompilationUnit getCompilationUnit(File file) {
    try (FileInputStream in = new FileInputStream(file)) {
      return StaticJavaParser.parse(in);
    }
  }

  @SneakyThrows
  private void expandWildcardImports(File file) {
    CompilationUnit compilationUnit = getCompilationUnit(file);

    Set<String> usedTypes = new HashSet<>();
    compilationUnit.accept(new VoidVisitorAdapter<Void>() {
      @Override
      public void visit(AnnotationDeclaration annotationDeclaration, Void arg) {
        usedTypes.add(annotationDeclaration.getNameAsString());
        super.visit(annotationDeclaration, arg);
      }

      @Override
      public void visit(FieldDeclaration field, Void arg) {
        field.getVariables().forEach(var -> usedTypes.add(var.getType().asString()));
        super.visit(field, arg);
      }

      @Override
      public void visit(ObjectCreationExpr objCreation, Void arg) {
        usedTypes.add(objCreation.getType().asString());
        super.visit(objCreation, arg);
      }

      @Override
      public void visit(VariableDeclarationExpr varDeclaration, Void arg) {
        varDeclaration.getVariables().forEach(var -> usedTypes.add(var.getType().asString()));
        super.visit(varDeclaration, arg);
      }
    }, null);

    List<Class<?>> classes = new ArrayList<>(usedTypes.size());
    List<ImportDeclaration> importsToRemove = new ArrayList<>();
    List<ImportDeclaration> imports = compilationUnit.getImports();
    for (ImportDeclaration imp : imports) {
      if (imp.isAsterisk()) {
        importsToRemove.add(imp);
      }
    }

    for (ImportDeclaration imp : importsToRemove) {
      String packageName = imp.getNameAsString();
      compilationUnit.remove(imp);
      for (String className : usedTypes) {
        try {
          String name = packageName + "." + className.replaceAll("\\s*<.*>\\s*", "");
          classes.add(Class.forName(name));
        } catch (ClassNotFoundException e) {
          getLog().warn("Class not found: " + packageName + "." + className);
        }
      }
    }

    for (Class<?> clazz : classes) {
      compilationUnit.addImport(clazz);
    }

    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(compilationUnit.toString().getBytes());
      out.flush();
    } catch (IOException e) {
      throw new IOException("Error writing to file: " + file, e);
    }

    getLog().info("Expanded wildcard imports in " + file);
  }
}
